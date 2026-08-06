package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.dto.integrity.IntegrityOverviewResponse;
import com.project.wastepilot.domain.dto.integrity.PatternReviewEntry;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.EntityType;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.project.wastepilot.service.IntegrityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrityServiceImpl implements IntegrityService {
  private final ActivityLogRepository activityLogRepository;
  private final AuditTrailRepository auditTrailRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final BatchRepository batchRepository;
  private final com.project.wastepilot.service.OperationsService operationsService;

  private static final int PATTERN_REVIEW_MIN_SAMPLE = 5;
  private static final BigDecimal PATTERN_REVIEW_SUSPICION_THRESHOLD = new BigDecimal("0.60");
  private static final BigDecimal CLOSE_VARIANCE_THRESHOLD = new BigDecimal("5");
  private static final BigDecimal PATTERN_REVIEW_BAND_POINTS = new BigDecimal("0.5");

  @Override
  @Transactional(readOnly = true)
  public List<ActivityLogEntryResponse> getActivityLogs(String batchId) {
    return activityLogRepository.findTop200ByOrderByTimestampDesc().stream()
        .map(this::toActivityResponse)
        .filter(entry -> batchId == null || batchId.isBlank() || batchId.equals(entry.batchId()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditTrailEntryResponse> getAuditTrail(String batchId) {
    return auditTrailRepository.findTop200ByOrderByTimestampDesc().stream()
        .map(this::toAuditResponse)
        .filter(entry -> batchId == null || batchId.isBlank() || batchId.equals(entry.batchId()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public IntegrityOverviewResponse getOverview() {
    return new IntegrityOverviewResponse(
        BigDecimal.valueOf(operationsService.getAverageConfidenceScoreForCompletedBatches()),
        operationsService.getOpenRedFlagsCount(),
        operationsService.getOverdueBatchClosuresCount()
    );
  }

  @Override
  @Transactional(readOnly = true)
  public List<PatternReviewEntry> getPatternReview() {
    List<BatchEntity> completedBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);
    
    Map<String, List<BatchEntity>> batchesByUser = completedBatches.stream()
        .filter(b -> b.getClosedBy() != null && !b.getClosedBy().isBlank())
        .collect(Collectors.groupingBy(BatchEntity::getClosedBy));
        
    List<PatternReviewEntry> results = new ArrayList<>();
    
    for (Map.Entry<String, List<BatchEntity>> entry : batchesByUser.entrySet()) {
      List<BatchEntity> userBatches = entry.getValue();
      if (userBatches.size() < PATTERN_REVIEW_MIN_SAMPLE) continue;
      
      int suspiciousCount = 0;
      for (BatchEntity batch : userBatches) {
        BigDecimal variance = operationsService.getBatchCloseSummary(batch.getId().toString()).variancePercent();
        
        BigDecimal lowerBound = CLOSE_VARIANCE_THRESHOLD.subtract(PATTERN_REVIEW_BAND_POINTS);
        BigDecimal upperBound = CLOSE_VARIANCE_THRESHOLD;
        
        if (variance.compareTo(lowerBound) >= 0 && variance.compareTo(upperBound) <= 0) {
          suspiciousCount++;
        }
      }
      
      BigDecimal suspicionPercent = new BigDecimal(suspiciousCount)
          .divide(new BigDecimal(userBatches.size()), 4, RoundingMode.HALF_UP);
          
      if (suspicionPercent.compareTo(PATTERN_REVIEW_SUSPICION_THRESHOLD) >= 0) {
        String note = String.format("%d of %d recent batch closes fall within 0.5%% below the variance threshold — pattern warrants review", 
            suspiciousCount, userBatches.size());
        results.add(new PatternReviewEntry(entry.getKey(), suspiciousCount, userBatches.size(), suspicionPercent, note));
      }
    }
    
    return results;
  }

  private ActivityLogEntryResponse toActivityResponse(ActivityLogEntity entity) {
    String details = entity.getDetail() == null ? "" : entity.getDetail();
    String source = details.contains("[ocr]") ? "ocr" : details.contains("[system]") ? "system" : "manual";
    String batchId = parseBatchIdFromDetails(details);
    if (batchId == null) {
      batchId = resolveBatchIdByEntity(entity.getEntity(), entity.getEntityId());
    }

    String cleanDetails = details
        .replaceAll("\\[batch:[^\\]]+\\]\\s*", "")
        .replace("[manual] ", "")
        .replace("[system] ", "")
        .replace("[ocr] ", "")
        .trim();
    return new ActivityLogEntryResponse(
        entity.getId().toString(),
        batchId,
        entity.getActor(),
        entity.getAction(),
        normalizeActivityEntity(entity.getEntity()),
        entity.getEntityId(),
        source,
        cleanDetails,
        entity.getTimestamp()
    );
  }

  private AuditTrailEntryResponse toAuditResponse(AuditTrailEntity entity) {
    String actor = entity.getActor();
    String reason = entity.getReason() == null ? "" : entity.getReason();
    String batchId = resolveBatchIdByEntity(entity.getEntity(), entity.getEntityId());
    return new AuditTrailEntryResponse(
        entity.getId().toString(),
        batchId,
        entity.getField(),
        entity.getOldValue(),
        entity.getNewValue(),
        actor,
        entity.getTimestamp(),
        reason
    );
  }

  private String resolveBatchIdByEntity(EntityType entityType, String entityId) {
    try {
      UUID id = UUID.fromString(entityId);
      if (entityType == EntityType.batch && batchRepository.existsById(id)) {
        return entityId;
      }
      if (entityType == EntityType.inventory) {
        InventoryLogEntity inventory = inventoryLogRepository.findById(id).orElse(null);
        return inventory != null && inventory.getBatch() != null ? inventory.getBatch().getId().toString() : null;
      }
      if (entityType == EntityType.waste) {
        WasteLogEntity waste = wasteLogRepository.findById(id).orElse(null);
        return waste != null ? waste.getBatch().getId().toString() : null;
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private String parseBatchIdFromDetails(String details) {
    int start = details.indexOf("[batch:");
    if (start < 0) {
      return null;
    }
    int end = details.indexOf("]", start);
    if (end <= start) {
      return null;
    }
    return details.substring(start + 7, end);
  }

  private String normalizeActivityEntity(EntityType entityType) {
    return switch (entityType) {
      case batch -> "batch";
      case inventory -> "inventory";
      case waste -> "waste";
      default -> "system";
    };
  }
}


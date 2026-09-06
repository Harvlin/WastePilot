package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.dto.integrity.IntegrityOverviewResponse;
import com.project.wastepilot.domain.dto.integrity.PatternReviewEntry;
import com.project.wastepilot.domain.dto.integrity.CrossValidationDiscrepancy;
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
  private static final int PATTERN_REVIEW_WINDOW = 20;
  private static final BigDecimal PATTERN_REVIEW_SUSPICION_THRESHOLD = new BigDecimal("0.60");
  private static final BigDecimal CLOSE_VARIANCE_THRESHOLD = new BigDecimal("5");
  private static final BigDecimal PATTERN_REVIEW_BAND_POINTS = new BigDecimal("0.5");
  private static final BigDecimal CROSS_VALIDATION_TOLERANCE = new BigDecimal("15");

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
      // Limit to the configured sample window (most recent first).
      List<BatchEntity> userBatches = entry.getValue().stream()
          .limit(PATTERN_REVIEW_WINDOW)
          .toList();

      // Require a full sample before flagging to avoid false positives on new users.
      if (userBatches.size() < PATTERN_REVIEW_MIN_SAMPLE) continue;

      int suspiciousCount = 0;
      for (BatchEntity batch : userBatches) {
        BigDecimal variance = operationsService.getBatchVariancePercent(batch.getId().toString());

        // Use absolute value: flag both over- and under-reporting just below the threshold.
        BigDecimal absVariance = variance.abs();
        BigDecimal lowerBound = CLOSE_VARIANCE_THRESHOLD.subtract(PATTERN_REVIEW_BAND_POINTS);
        BigDecimal upperBound = CLOSE_VARIANCE_THRESHOLD;

        if (absVariance.compareTo(lowerBound) >= 0 && absVariance.compareTo(upperBound) <= 0) {
          suspiciousCount++;
        }
      }

      BigDecimal suspicionPercent = new BigDecimal(suspiciousCount)
          .divide(new BigDecimal(userBatches.size()), 4, RoundingMode.HALF_UP);

      if (suspicionPercent.compareTo(PATTERN_REVIEW_SUSPICION_THRESHOLD) >= 0) {
        String note = String.format(
            "%d of %d recent batch closes fall within %s%% below the variance threshold — pattern warrants review",
            suspiciousCount, userBatches.size(), PATTERN_REVIEW_BAND_POINTS.stripTrailingZeros().toPlainString());
        results.add(new PatternReviewEntry(entry.getKey(), suspiciousCount, userBatches.size(), suspicionPercent, note));
      }
    }

    return results;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CrossValidationDiscrepancy> getCrossValidationDiscrepancies() {
    List<BatchEntity> completedBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);
    if (completedBatches.isEmpty()) return List.of();

    List<UUID> batchIds = completedBatches.stream().map(BatchEntity::getId).toList();
    List<InventoryLogEntity> allInLogs = inventoryLogRepository.findByBatch_IdInAndType(batchIds, com.project.wastepilot.domain.enums.InventoryType.IN);

    // Group by batchId, then by materialName
    Map<UUID, Map<String, List<InventoryLogEntity>>> groupedLogs = allInLogs.stream()
        .collect(Collectors.groupingBy(
            log -> log.getBatch().getId(),
            Collectors.groupingBy(InventoryLogEntity::getMaterialName)
        ));

    List<CrossValidationDiscrepancy> discrepancies = new ArrayList<>();

    for (Map.Entry<UUID, Map<String, List<InventoryLogEntity>>> batchEntry : groupedLogs.entrySet()) {
      UUID batchId = batchEntry.getKey();
      
      for (Map.Entry<String, List<InventoryLogEntity>> materialEntry : batchEntry.getValue().entrySet()) {
        String materialName = materialEntry.getKey();
        List<InventoryLogEntity> logs = materialEntry.getValue();

        BigDecimal manualKg = BigDecimal.ZERO;
        BigDecimal sensorKg = BigDecimal.ZERO;
        boolean hasManual = false;
        boolean hasSensor = false;

        for (InventoryLogEntity log : logs) {
          String source = log.getSource() == null ? "" : log.getSource().toLowerCase();
          if (source.equals("sensor")) {
            sensorKg = sensorKg.add(log.getQuantity());
            hasSensor = true;
          } else if (source.equals("manual") || source.equals("ocr")) {
            manualKg = manualKg.add(log.getQuantity());
            hasManual = true;
          }
        }

        if (hasManual && hasSensor) {
          BigDecimal diff = manualKg.subtract(sensorKg).abs();
          BigDecimal max = manualKg.max(sensorKg);
          
          if (max.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal relativeDiff = diff.divide(max, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            
            if (relativeDiff.compareTo(CROSS_VALIDATION_TOLERANCE) > 0) {
              String note = String.format("A discrepancy of %s%% detected between manual/OCR entry (%s kg) and sensor reading (%s kg).",
                  relativeDiff.setScale(1, RoundingMode.HALF_UP).toPlainString(),
                  manualKg.setScale(1, RoundingMode.HALF_UP).toPlainString(),
                  sensorKg.setScale(1, RoundingMode.HALF_UP).toPlainString());
              
              discrepancies.add(new CrossValidationDiscrepancy(
                  batchId.toString(),
                  materialName,
                  manualKg,
                  sensorKg,
                  relativeDiff,
                  note
              ));
            }
          }
        }
      }
    }

    return discrepancies;
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
        normalizeActivityEntity(entity.getEntity(), entity.getAction()),
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

  private String normalizeActivityEntity(EntityType entityType, String action) {
    if (action != null && action.startsWith("score_")) {
      return "score";
    }
    return switch (entityType) {
      case batch -> "batch";
      case inventory -> "inventory";
      case waste -> "waste";
      case material, template, insight, anomaly, settings -> "system";
      default -> "system";
    };
  }
}


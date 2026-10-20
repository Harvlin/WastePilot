package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditChainVerificationResponse;
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
import com.project.wastepilot.domain.enums.InventoryType;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.project.wastepilot.service.IntegrityService;
import com.project.wastepilot.service.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrityServiceImpl implements IntegrityService {

  private final ActivityLogRepository activityLogRepository;
  private final AuditTrailRepository auditTrailRepository;
  private final AuditChainService auditChainService;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final BatchRepository batchRepository;
  private final com.project.wastepilot.service.OperationsService operationsService;
  private final WastePilotThresholds thresholds;

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
  public AuditChainVerificationResponse verifyAuditChain() {
    return auditChainService.verify();
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

    // Batch-fetch all inventory IN logs for every candidate batch in one query.
    // This eliminates the N+1 that the previous per-batch getBatchVariancePercent() call caused.
    Set<UUID> candidateIds = batchesByUser.values().stream()
        .flatMap(List::stream)
        .map(BatchEntity::getId)
        .collect(Collectors.toSet());
    Map<UUID, List<InventoryLogEntity>> inLogsByBatch = inventoryLogRepository
        .findByBatch_IdInAndType(new ArrayList<>(candidateIds), InventoryType.IN)
        .stream()
        .collect(Collectors.groupingBy(log -> log.getBatch().getId()));

    List<PatternReviewEntry> results = new ArrayList<>();

    for (Map.Entry<String, List<BatchEntity>> entry : batchesByUser.entrySet()) {
      List<BatchEntity> userBatches = entry.getValue().stream()
          .limit(thresholds.getPatternReviewWindow())
          .toList();

      if (userBatches.size() < thresholds.getPatternReviewMinSample()) continue;

      int suspiciousCount = 0;
      for (BatchEntity batch : userBatches) {
        // Compute variance using the pre-fetched inventory logs — no additional DB call per batch.
        BigDecimal variance = computeVarianceFromLogs(batch, inLogsByBatch.getOrDefault(batch.getId(), List.of()));
        BigDecimal absVariance = variance.abs();
        BigDecimal threshold = BigDecimal.valueOf(thresholds.getCloseVarianceThresholdPct());
        BigDecimal band = BigDecimal.valueOf(thresholds.getPatternReviewBandPoints());
        BigDecimal lowerBound = threshold.subtract(band);

        if (absVariance.compareTo(lowerBound) >= 0 && absVariance.compareTo(threshold) <= 0) {
          suspiciousCount++;
        }
      }

      BigDecimal suspicionPercent = new BigDecimal(suspiciousCount)
          .divide(new BigDecimal(userBatches.size()), 4, RoundingMode.HALF_UP);

      if (suspicionPercent.compareTo(BigDecimal.valueOf(thresholds.getPatternReviewSuspicionThreshold())) >= 0) {
        String note = String.format(
            "%d of %d recent batch closes fall within %s%% below the variance threshold — pattern warrants review",
            suspiciousCount, userBatches.size(),
            BigDecimal.valueOf(thresholds.getPatternReviewBandPoints()).stripTrailingZeros().toPlainString());
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
    List<InventoryLogEntity> allInLogs = inventoryLogRepository.findByBatch_IdInAndType(batchIds, InventoryType.IN);

    Map<UUID, Map<String, List<InventoryLogEntity>>> groupedLogs = allInLogs.stream()
        .collect(Collectors.groupingBy(
            log -> log.getBatch().getId(),
            Collectors.groupingBy(InventoryLogEntity::getMaterialName)
        ));

    BigDecimal tolerance = BigDecimal.valueOf(thresholds.getCrossValidationTolerancePct());
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
          if (source.startsWith("sensor")) {
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
            if (relativeDiff.compareTo(tolerance) > 0) {
              String note = String.format(
                  "A discrepancy of %s%% detected between manual/OCR entry (%s kg) and sensor reading (%s kg).",
                  relativeDiff.setScale(1, RoundingMode.HALF_UP).toPlainString(),
                  manualKg.setScale(1, RoundingMode.HALF_UP).toPlainString(),
                  sensorKg.setScale(1, RoundingMode.HALF_UP).toPlainString());
              discrepancies.add(new CrossValidationDiscrepancy(
                  batchId.toString(), materialName, manualKg, sensorKg, relativeDiff, note));
            }
          }
        }
      }
    }

    return discrepancies;
  }

  /**
   * Compute variance percent from pre-fetched IN logs (avoids a per-batch DB call).
   * Uses the input quantity captured when the batch started, not the current template.
   * Returns -100.00 sentinel when no IN logs exist.
   */
  private BigDecimal computeVarianceFromLogs(BatchEntity batch, List<InventoryLogEntity> inLogs) {
    if (inLogs.isEmpty()) return new BigDecimal("-100.00");
    BigDecimal measured = inLogs.stream().map(InventoryLogEntity::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (measured.compareTo(BigDecimal.ZERO) <= 0) return new BigDecimal("-100.00");

    BigDecimal planned = batch.getPlannedInputKg();
    if (planned == null) {
      throw new IllegalStateException("Batch " + batch.getId() + " is missing its planned-input snapshot.");
    }

    return planned.compareTo(BigDecimal.ZERO) > 0
        ? measured.subtract(planned).multiply(new BigDecimal("100"))
            .divide(planned, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
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
    String reason = entity.getReason() == null ? "" : entity.getReason();
    String batchId = resolveBatchIdByEntity(entity.getEntity(), entity.getEntityId());
    return new AuditTrailEntryResponse(
        entity.getId().toString(),
        batchId,
        entity.getField(),
        entity.getOldValue(),
        entity.getNewValue(),
        entity.getActor(),
        entity.getTimestamp(),
        reason
    );
  }

  private String resolveBatchIdByEntity(EntityType entityType, String entityId) {
    try {
      UUID id = UUID.fromString(entityId);
      if (entityType == EntityType.batch && batchRepository.existsById(id)) return entityId;
      if (entityType == EntityType.inventory) {
        InventoryLogEntity inv = inventoryLogRepository.findById(id).orElse(null);
        return inv != null && inv.getBatch() != null ? inv.getBatch().getId().toString() : null;
      }
      if (entityType == EntityType.waste) {
        WasteLogEntity waste = wasteLogRepository.findById(id).orElse(null);
        return waste != null ? waste.getBatch().getId().toString() : null;
      }
    } catch (Exception ignored) {}
    return null;
  }

  private String parseBatchIdFromDetails(String details) {
    int start = details.indexOf("[batch:");
    if (start < 0) return null;
    int end = details.indexOf("]", start);
    if (end <= start) return null;
    return details.substring(start + 7, end);
  }

  private String normalizeActivityEntity(EntityType entityType, String action) {
    if (action != null && action.startsWith("score_")) return "score";
    return switch (entityType) {
      case batch -> "batch";
      case inventory -> "inventory";
      case waste -> "waste";
      case material, template, insight, anomaly, settings -> "system";
      default -> "system";
    };
  }
}

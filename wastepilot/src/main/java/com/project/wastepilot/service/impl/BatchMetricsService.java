package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.RedFlagEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.RedFlagSeverity;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchMetricsService {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final Duration OVERDUE_HOURS = Duration.ofHours(24);

  private final BatchRepository batchRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final AuditTrailRepository auditTrailRepository;
  private final RedFlagRepository redFlagRepository;
  private final WastePilotThresholds thresholds;

  @Transactional(readOnly = true)
  public int getOverdueBatchClosuresCount() {
    Instant now = Instant.now();
    return (int) batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.running).stream()
        .filter(batch -> Duration.between(batch.getStartedAt(), now).compareTo(OVERDUE_HOURS) > 0)
        .count();
  }

  @Transactional(readOnly = true)
  public int getOpenRedFlagsCount() {
    return Math.toIntExact(redFlagRepository.countByResolvedFalse());
  }

  @Transactional(readOnly = true)
  public int getAverageConfidenceScoreForCompletedBatches() {
    List<BatchEntity> completed = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);
    if (completed.isEmpty()) {
      return 100;
    }

    List<UUID> batchIds = completed.stream().map(BatchEntity::getId).toList();
    Map<UUID, List<WasteLogEntity>> wasteByBatch = wasteLogRepository
        .findByBatchIdInOrderByTimestampDesc(batchIds)
        .stream().collect(Collectors.groupingBy(w -> w.getBatch().getId()));
    Map<UUID, List<InventoryLogEntity>> inventoryByBatch = inventoryLogRepository
        .findByBatchIdInOrderByTimestampDesc(batchIds)
        .stream().collect(Collectors.groupingBy(i -> i.getBatch().getId()));

    List<String> entityIdStrings = batchIds.stream().map(UUID::toString).toList();
    List<AuditTrailEntity> auditEntries = auditTrailRepository.findByEntityIdIn(entityIdStrings);
    Map<String, Long> auditCountByBatch = auditEntries.stream()
        .collect(Collectors.groupingBy(AuditTrailEntity::getEntityId, Collectors.counting()));
    Map<String, Long> auditMeaningfulByBatch = auditEntries.stream()
        .filter(entry -> entry.getReason() != null && entry.getReason().length() >= 10)
        .collect(Collectors.groupingBy(AuditTrailEntity::getEntityId, Collectors.counting()));

    int sum = 0;
    for (BatchEntity batch : completed) {
      List<WasteLogEntity> wasteLogs = wasteByBatch.getOrDefault(batch.getId(), List.of());
      List<InventoryLogEntity> allLogs = inventoryByBatch.getOrDefault(batch.getId(), List.of());
      boolean hasInventorySignal = allLogs.stream().anyMatch(log -> log.getType() == InventoryType.OUT);
      boolean hasWasteSignal = !wasteLogs.isEmpty();
      boolean hasOutputSignal = batch.getOutputUnits() != null && batch.getOutputUnits().compareTo(ZERO) > 0;

      BigDecimal completeness = BigDecimal.valueOf(
          (hasInventorySignal ? 1 : 0) + (hasWasteSignal ? 1 : 0) + (hasOutputSignal ? 1 : 0))
          .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
      long totalAudit = auditCountByBatch.getOrDefault(batch.getId().toString(), 0L);
      long meaningfulAudit = auditMeaningfulByBatch.getOrDefault(batch.getId().toString(), 0L);
      BigDecimal auditIntegrity = totalAudit == 0 ? BigDecimal.ONE
          : BigDecimal.valueOf(meaningfulAudit).divide(BigDecimal.valueOf(totalAudit), 4, RoundingMode.HALF_UP);

      sum += score(completeness, BigDecimal.ONE, auditIntegrity);
    }
    return Math.round((float) sum / completed.size());
  }

  @Transactional(readOnly = true)
  public BatchCloseSummaryResponse buildBatchCloseSummary(BatchEntity batch, BigDecimal outputUnitsOverride) {
    BigDecimal outputUnits = outputUnitsOverride == null ? batch.getOutputUnits() : outputUnitsOverride;
    BigDecimal plannedInputKg = requirePlannedInput(batch);
    BigDecimal variancePercent = calculateVariancePercent(batch);

    List<WasteLogEntity> wasteLogs = wasteLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId());
    BigDecimal wasteTotalKg = wasteLogs.isEmpty()
        ? batch.getWasteKg().max(ZERO)
        : wasteLogs.stream().map(WasteLogEntity::getQuantityKg).reduce(ZERO, BigDecimal::add);
    BigDecimal reuseKg = sumWasteByDestination(wasteLogs, WasteDestination.reuse);
    BigDecimal repairKg = sumWasteByDestination(wasteLogs, WasteDestination.repair);
    BigDecimal disposeKg = sumWasteByDestination(wasteLogs, WasteDestination.dispose);
    BigDecimal landfillShare = wasteTotalKg.compareTo(ZERO) > 0
        ? scale(disposeKg.divide(wasteTotalKg, 6, RoundingMode.HALF_UP), 4)
        : ZERO;

    List<InventoryLogEntity> inventoryLogs = inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId());
    List<InventoryLogEntity> inLogs = inventoryLogs.stream()
        .filter(log -> log.getType() == InventoryType.IN)
        .toList();
    BigDecimal measuredInputKg = inLogs.stream()
        .map(InventoryLogEntity::getQuantity)
        .reduce(ZERO, BigDecimal::add);
    boolean hasRealInput = !inLogs.isEmpty() && measuredInputKg.compareTo(ZERO) > 0;
    String actualInputSource = hasRealInput ? "measured" : "not-tracked";
    BigDecimal actualInputKg = hasRealInput ? measuredInputKg : plannedInputKg;

    BigDecimal landfillIntensity = outputUnits.compareTo(ZERO) > 0
        ? scale(disposeKg.divide(outputUnits, 6, RoundingMode.HALF_UP), 4)
        : scale(disposeKg, 4);
    boolean overdue = batch.getStatus() == BatchStatus.running
        && Duration.between(batch.getStartedAt(), Instant.now()).compareTo(OVERDUE_HOURS) > 0;
    boolean hasInventorySignal = inventoryLogs.stream().anyMatch(log -> log.getType() == InventoryType.OUT);
    boolean hasWasteSignal = !wasteLogs.isEmpty();
    boolean hasOutputSignal = outputUnits.compareTo(ZERO) > 0;
    BigDecimal completeness = BigDecimal.valueOf(
        (hasInventorySignal ? 1 : 0) + (hasWasteSignal ? 1 : 0) + (hasOutputSignal ? 1 : 0))
        .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);

    List<String> entityIds = new ArrayList<>();
    entityIds.add(batch.getId().toString());
    inventoryLogs.forEach(log -> entityIds.add(log.getId().toString()));
    wasteLogs.forEach(log -> entityIds.add(log.getId().toString()));
    List<AuditTrailEntity> auditEntries = auditTrailRepository.findByEntityIdIn(entityIds);
    long meaningfulReasons = auditEntries.stream()
        .map(AuditTrailEntity::getReason)
        .filter(reason -> reason != null && !reason.isBlank() && reason.length() >= 10)
        .count();
    BigDecimal auditIntegrity = auditEntries.isEmpty() ? BigDecimal.ONE
        : BigDecimal.valueOf(meaningfulReasons).divide(BigDecimal.valueOf(auditEntries.size()), 4, RoundingMode.HALF_UP);
    int confidenceScore = score(completeness, overdue ? new BigDecimal("0.72") : BigDecimal.ONE, auditIntegrity);
    String confidenceLevel = confidenceScore >= 85 ? "high" : confidenceScore >= 65 ? "medium" : "low";

    List<BatchCloseSummaryResponse.BatchRedFlagResponse> redFlags =
        buildSummaryFlags(batch, variancePercent, landfillShare, overdue);

    return new BatchCloseSummaryResponse(
        batch.getId().toString(),
        batch.getTemplateName(),
        batch.getStartedAt(),
        overdue,
        scale(plannedInputKg, 3),
        scale(actualInputKg, 3),
        actualInputSource,
        scale(outputUnits, 3),
        scale(wasteTotalKg, 3),
        scale(reuseKg, 3),
        scale(repairKg, 3),
        scale(disposeKg, 3),
        landfillIntensity,
        variancePercent,
        confidenceLevel,
        confidenceScore,
        new BatchCloseSummaryResponse.ConfidenceBreakdown(
            scale(completeness, 3),
            overdue ? new BigDecimal("0.720") : BigDecimal.ONE.setScale(3),
            scale(auditIntegrity, 3)),
        redFlags,
        landfillShare
    );
  }

  @Transactional(readOnly = true)
  public BigDecimal calculateVariancePercent(BatchEntity batch) {
    BigDecimal plannedInputKg = requirePlannedInput(batch);
    List<InventoryLogEntity> inLogs = inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())
        .stream()
        .filter(log -> log.getType() == InventoryType.IN)
        .toList();
    if (inLogs.isEmpty()) {
      return new BigDecimal("-100.00");
    }

    BigDecimal measuredInputKg = inLogs.stream()
        .map(InventoryLogEntity::getQuantity)
        .reduce(ZERO, BigDecimal::add);
    if (measuredInputKg.compareTo(ZERO) <= 0) {
      return new BigDecimal("-100.00");
    }

    return plannedInputKg.compareTo(ZERO) > 0
        ? scale(measuredInputKg.subtract(plannedInputKg).multiply(new BigDecimal("100"))
            .divide(plannedInputKg, 6, RoundingMode.HALF_UP), 2)
        : ZERO;
  }

  private BigDecimal requirePlannedInput(BatchEntity batch) {
    if (batch.getPlannedInputKg() == null) {
      throw new IllegalStateException("Batch " + batch.getId() + " is missing its planned-input snapshot.");
    }
    return batch.getPlannedInputKg();
  }

  private int score(BigDecimal completeness, BigDecimal timeliness, BigDecimal auditIntegrity) {
    return BigDecimal.valueOf(100)
        .multiply(completeness.multiply(new BigDecimal("0.5"))
            .add(timeliness.multiply(new BigDecimal("0.3")))
            .add(auditIntegrity.multiply(new BigDecimal("0.2"))))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  private List<BatchCloseSummaryResponse.BatchRedFlagResponse> buildSummaryFlags(
      BatchEntity batch,
      BigDecimal variancePercent,
      BigDecimal landfillShare,
      boolean overdue
  ) {
    List<BatchCloseSummaryResponse.BatchRedFlagResponse> persistedFlags =
        redFlagRepository.findByRelatedBatchIdAndResolvedFalse(batch.getId()).stream()
            .map(flag -> toRedFlagResponse(flag, "system"))
            .toList();
    List<BatchCloseSummaryResponse.BatchRedFlagResponse> generatedFlags = new ArrayList<>();

    if (variancePercent.compareTo(new BigDecimal("-100.00")) == 0) {
      generatedFlags.add(generatedFlag(batch, "no-input", "high", "no-input-tracked",
          "No inventory IN logs have been recorded for this batch. Input tracking is required before closing."));
    } else if (variancePercent.abs().compareTo(
        BigDecimal.valueOf(thresholds.getCloseVarianceThresholdPct())) > 0) {
      generatedFlags.add(generatedFlag(batch, "variance", "medium", "variance",
          "Variance " + variancePercent.stripTrailingZeros().toPlainString()
              + "% requires reviewer justification on close."));
    }
    if (landfillShare.compareTo(new BigDecimal("0.4")) > 0) {
      generatedFlags.add(generatedFlag(batch, "landfill", "high", "landfill-risk",
          "Landfill share exceeds 40%. Batch score cap will be applied."));
    }
    if (overdue) {
      generatedFlags.add(generatedFlag(batch, "overdue", "medium", "overdue-close",
          "Batch close is overdue and will reduce confidence."));
    }
    generatedFlags.addAll(persistedFlags);
    return generatedFlags;
  }

  private BatchCloseSummaryResponse.BatchRedFlagResponse generatedFlag(
      BatchEntity batch,
      String suffix,
      String severity,
      String type,
      String message
  ) {
    return new BatchCloseSummaryResponse.BatchRedFlagResponse(
        "RF-S-" + batch.getId() + "-" + suffix,
        batch.getId().toString(),
        severity,
        type,
        message,
        Instant.now(),
        null,
        null
    );
  }

  private BatchCloseSummaryResponse.BatchRedFlagResponse toRedFlagResponse(
      RedFlagEntity flag,
      String resolvedBy
  ) {
    String type = flag.getSeverity() == RedFlagSeverity.high ? "landfill-risk"
        : flag.getSeverity() == RedFlagSeverity.medium ? "variance"
        : "overdue-close";
    return new BatchCloseSummaryResponse.BatchRedFlagResponse(
        flag.getId().toString(),
        flag.getRelatedBatchId() == null ? "" : flag.getRelatedBatchId().toString(),
        flag.getSeverity().name(),
        type,
        flag.getMessage(),
        flag.getCreatedAt(),
        flag.getResolvedAt(),
        resolvedBy
    );
  }

  private BigDecimal sumWasteByDestination(List<WasteLogEntity> wasteLogs, WasteDestination destination) {
    return wasteLogs.stream()
        .filter(log -> log.getDestination() == destination)
        .map(WasteLogEntity::getQuantityKg)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal scale(BigDecimal value, int scale) {
    return value.setScale(scale, RoundingMode.HALF_UP);
  }
}

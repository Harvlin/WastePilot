package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.dto.operations.BatchResponse;
import com.project.wastepilot.domain.dto.operations.CloseBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateInventoryLogRequest;
import com.project.wastepilot.domain.dto.operations.CreateWasteLogRequest;
import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.operations.OperationsPayloadResponse;
import com.project.wastepilot.domain.dto.operations.RecoverWasteRequest;
import com.project.wastepilot.domain.dto.operations.WasteLogResponse;
import com.project.wastepilot.domain.dto.operations.WasteRecoveryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.RedFlagEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.EntityType;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.RecoveryStatus;
import com.project.wastepilot.domain.enums.RedFlagSeverity;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationsService {
  private static final BigDecimal ONE_THOUSANDTH = new BigDecimal("0.001");
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final int CLOSE_VARIANCE_THRESHOLD = 5;
  private static final Duration OVERDUE_HOURS = Duration.ofHours(24);

  private record RecoveryMeta(UUID inventoryLogId, Instant recoveredAt) {}

  private final BatchRepository batchRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final TemplateRepository templateRepository;
  private final ActivityLogRepository activityLogRepository;
  private final AuditTrailRepository auditTrailRepository;
  private final RedFlagRepository redFlagRepository;
  private final ConcurrentHashMap<UUID, RecoveryMeta> recoveryMeta = new ConcurrentHashMap<>();

  @Transactional(readOnly = true)
  public OperationsPayloadResponse getOperationsPayload() {
    List<BatchResponse> batches = batchRepository.findAll().stream()
        .sorted(Comparator.comparing(BatchEntity::getStartedAt).reversed())
        .map(this::toBatchResponse)
        .toList();
    List<InventoryLogResponse> inventoryLogs = inventoryLogRepository.findTop200ByOrderByTimestampDesc().stream()
        .map(this::toInventoryLogResponse)
        .toList();
    List<WasteLogResponse> wasteLogs = wasteLogRepository.findTop200ByOrderByTimestampDesc().stream()
        .map(this::toWasteLogResponse)
        .toList();
    return new OperationsPayloadResponse(batches, inventoryLogs, wasteLogs);
  }

  @Transactional
  public BatchResponse createBatch(CreateBatchRequest request) {
    BatchEntity batch = new BatchEntity();
    batch.setTemplateName(request.templateName().trim());
    batch.setStartedAt(Instant.now());
    batch.setOutputUnits(request.outputUnits());
    batch.setWasteKg(request.wasteKg());
    batch.setStatus(BatchStatus.running);
    BatchEntity saved = batchRepository.save(batch);
    logActivity(EntityType.batch, saved.getId().toString(), "batch_started", saved.getId().toString(), "manual",
        "Started using " + saved.getTemplateName() + ".");
    return toBatchResponse(saved);
  }

  @Transactional
  public InventoryLogResponse createInventoryLog(CreateInventoryLogRequest request) {
    InventoryType type = parseInventoryType(request.type());
    BatchEntity batch = null;
    if (request.batchId() != null && !request.batchId().isBlank()) {
      batch = resolveBatch(request.batchId());
    }
    if (type == InventoryType.OUT && batch == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_REQUIRED", "Batch is required for inventory OUT logs.");
    }

    InventoryLogEntity entity = new InventoryLogEntity();
    entity.setBatch(batch);
    entity.setMaterialName(request.materialName().trim());
    entity.setType(type);
    entity.setQuantity(request.quantity());
    entity.setUnit(request.unit().trim());
    entity.setSource(request.source().trim());
    entity.setTimestamp(Instant.now());
    InventoryLogEntity saved = inventoryLogRepository.save(entity);

    logActivity(
        EntityType.inventory,
        saved.getId().toString(),
        "inventory_logged",
        batch == null ? null : batch.getId().toString(),
        "OCR".equalsIgnoreCase(saved.getSource()) ? "ocr" : "manual",
        saved.getType() + " " + saved.getQuantity() + saved.getUnit() + " " + saved.getMaterialName()
    );

    return toInventoryLogResponse(saved);
  }

  @Transactional
  public WasteLogResponse createWasteLog(CreateWasteLogRequest request) {
    BatchEntity batch = resolveBatch(request.batchId());
    WasteDestination destination = parseDestination(request.destination());

    WasteLogEntity entity = new WasteLogEntity();
    entity.setBatch(batch);
    entity.setMaterialName(request.materialName().trim());
    entity.setQuantityKg(request.quantityKg());
    entity.setDestination(destination);
    entity.setRecoveryStatus(destination == WasteDestination.dispose ? RecoveryStatus.not_applicable : RecoveryStatus.pending);
    entity.setAiSuggestedAction(resolveAiSuggestion(request, destination));
    entity.setTimestamp(Instant.now());
    WasteLogEntity saved = wasteLogRepository.save(entity);

    logActivity(
        EntityType.waste,
        saved.getId().toString(),
        "waste_logged",
        batch.getId().toString(),
        "manual",
        saved.getQuantityKg() + "kg to " + saved.getDestination() + "."
    );
    return toWasteLogResponse(saved);
  }

  @Transactional
  public WasteRecoveryResponse recoverWasteToInventory(RecoverWasteRequest request) {
    UUID wasteLogId = parseUuid(request.wasteLogId(), "wasteLogId");
    WasteLogEntity wasteLog = wasteLogRepository.lockById(wasteLogId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WASTE_LOG_NOT_FOUND", "Waste log not found."));
    if (wasteLog.getDestination() == WasteDestination.dispose) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WASTE_NOT_RECOVERABLE", "Dispose logs cannot be converted to recovered inventory.");
    }
    if (wasteLog.getRecoveryStatus() == RecoveryStatus.converted) {
      throw new ApiException(HttpStatus.CONFLICT, "WASTE_ALREADY_RECOVERED", "This waste log has already been converted to inventory.");
    }
    if (wasteLog.getQuantityKg().compareTo(ONE_THOUSANDTH) < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECOVERY_QTY", "Recovered quantity must be greater than zero.");
    }

    InventoryLogEntity inventoryLog = new InventoryLogEntity();
    inventoryLog.setBatch(wasteLog.getBatch());
    inventoryLog.setMaterialName(wasteLog.getMaterialName());
    inventoryLog.setType(InventoryType.IN);
    inventoryLog.setQuantity(wasteLog.getQuantityKg());
    inventoryLog.setUnit("kg");
    inventoryLog.setSource("Recovered");
    inventoryLog.setTimestamp(Instant.now());
    InventoryLogEntity savedInventory = inventoryLogRepository.save(inventoryLog);

    wasteLog.setRecoveryStatus(RecoveryStatus.converted);
    wasteLogRepository.save(wasteLog);
    recoveryMeta.put(wasteLog.getId(), new RecoveryMeta(savedInventory.getId(), Instant.now()));

    logActivity(
        EntityType.inventory,
        savedInventory.getId().toString(),
        "waste_recovered_to_inventory",
        wasteLog.getBatch().getId().toString(),
        "system",
        wasteLog.getMaterialName() + " " + wasteLog.getQuantityKg() + "kg converted from " + wasteLog.getId()
    );
    logAudit(
        EntityType.waste,
        wasteLog.getId().toString(),
        "recoveryStatus",
        "pending",
        "converted",
        "Recovered waste converted to inventory IN."
    );

    return new WasteRecoveryResponse(toWasteLogResponse(wasteLog), toInventoryLogResponse(savedInventory));
  }

  @Transactional(readOnly = true)
  public BatchCloseSummaryResponse getBatchCloseSummary(String batchId) {
    BatchEntity batch = resolveBatch(batchId);
    return buildBatchCloseSummary(batch, null);
  }

  @Transactional
  public BatchCloseSummaryResponse closeBatch(CloseBatchRequest request) {
    UUID batchId = parseUuid(request.batchId(), "batchId");
    BatchEntity batch = batchRepository.lockById(batchId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));
    if (batch.getStatus() != BatchStatus.running) {
      throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_CLOSED", "This batch is already closed.");
    }

    BatchCloseSummaryResponse preview = buildBatchCloseSummary(batch, request.outputUnits());
    if (preview.variancePercent().abs().compareTo(BigDecimal.valueOf(CLOSE_VARIANCE_THRESHOLD)) > 0
        && (request.closeReason() == null || request.closeReason().trim().isEmpty())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "CLOSE_REASON_REQUIRED",
          "Variance above " + CLOSE_VARIANCE_THRESHOLD + "% requires a close reason."
      );
    }

    BigDecimal previousOutput = batch.getOutputUnits();
    batch.setStatus(BatchStatus.completed);
    batch.setOutputUnits(request.outputUnits());
    batch.setWasteKg(preview.wasteTotalKg());
    batch.setClosedAt(Instant.now());
    batch.setClosedBy(resolveActor());
    batch.setCloseReason(request.closeReason() == null || request.closeReason().isBlank()
        ? "Confirmed by operator."
        : request.closeReason().trim());
    batchRepository.save(batch);

    if (previousOutput.compareTo(request.outputUnits()) != 0) {
      logAudit(
          EntityType.batch,
          batch.getId().toString(),
          "outputUnits",
          previousOutput.stripTrailingZeros().toPlainString(),
          request.outputUnits().stripTrailingZeros().toPlainString(),
          "Final count on batch closure."
      );
    }
    logActivity(EntityType.batch, batch.getId().toString(), "batch_closed", batch.getId().toString(), "manual", batch.getCloseReason());
    return buildBatchCloseSummary(batch, batch.getOutputUnits());
  }

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
    int sum = completed.stream()
        .map(batch -> buildBatchCloseSummary(batch, batch.getOutputUnits()).confidenceScore())
        .reduce(0, Integer::sum);
    return Math.round((float) sum / completed.size());
  }

  private BatchCloseSummaryResponse buildBatchCloseSummary(BatchEntity batch, BigDecimal outputUnitsOverride) {
    BigDecimal outputUnits = outputUnitsOverride == null ? batch.getOutputUnits() : outputUnitsOverride;
    TemplateEntity template = templateRepository.findByNameIgnoreCase(batch.getTemplateName()).orElse(null);
    BigDecimal plannedInputKg = template == null
        ? outputUnits.multiply(new BigDecimal("0.08")).max(new BigDecimal("12"))
        : template.getLines().stream()
            .map(line -> line.getQuantity())
            .reduce(ZERO, BigDecimal::add);

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
    BigDecimal actualInputKg = plannedInputKg.add(wasteTotalKg.multiply(new BigDecimal("0.45")))
        .max(plannedInputKg.multiply(new BigDecimal("0.8")));
    BigDecimal landfillIntensity = outputUnits.compareTo(ZERO) > 0
        ? scale(disposeKg.divide(outputUnits, 6, RoundingMode.HALF_UP), 4)
        : scale(disposeKg, 4);
    BigDecimal variancePercent = plannedInputKg.compareTo(ZERO) > 0
        ? scale(actualInputKg.subtract(plannedInputKg).multiply(new BigDecimal("100")).divide(plannedInputKg, 6, RoundingMode.HALF_UP), 2)
        : ZERO;

    boolean overdue = batch.getStatus() == BatchStatus.running
        && Duration.between(batch.getStartedAt(), Instant.now()).compareTo(OVERDUE_HOURS) > 0;
    boolean hasInventorySignal = inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId()).stream()
        .anyMatch(log -> log.getType() == InventoryType.OUT);
    boolean hasWasteSignal = !wasteLogs.isEmpty();
    boolean hasOutputSignal = outputUnits.compareTo(ZERO) > 0;
    BigDecimal completeness = BigDecimal.valueOf((hasInventorySignal ? 1 : 0) + (hasWasteSignal ? 1 : 0) + (hasOutputSignal ? 1 : 0))
        .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
    BigDecimal timeliness = overdue ? new BigDecimal("0.72") : BigDecimal.ONE;
    BigDecimal auditIntegrity = BigDecimal.ONE;
    int confidenceScore = BigDecimal.valueOf(100)
        .multiply(completeness.multiply(new BigDecimal("0.5"))
            .add(timeliness.multiply(new BigDecimal("0.3")))
            .add(auditIntegrity.multiply(new BigDecimal("0.2"))))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
    String confidenceLevel = confidenceScore >= 85 ? "high" : confidenceScore >= 65 ? "medium" : "low";

    List<BatchCloseSummaryResponse.BatchRedFlagResponse> redFlags = buildSummaryFlags(batch, variancePercent, landfillShare, overdue);

    return new BatchCloseSummaryResponse(
        batch.getId().toString(),
        batch.getTemplateName(),
        batch.getStartedAt(),
        overdue,
        scale(plannedInputKg, 3),
        scale(actualInputKg, 3),
        scale(outputUnits, 3),
        scale(wasteTotalKg, 3),
        scale(reuseKg, 3),
        scale(repairKg, 3),
        scale(disposeKg, 3),
        landfillIntensity,
        variancePercent,
        confidenceLevel,
        confidenceScore,
        new BatchCloseSummaryResponse.ConfidenceBreakdown(scale(completeness, 3), scale(timeliness, 3), scale(auditIntegrity, 3)),
        redFlags,
        landfillShare
    );
  }

  private List<BatchCloseSummaryResponse.BatchRedFlagResponse> buildSummaryFlags(
      BatchEntity batch,
      BigDecimal variancePercent,
      BigDecimal landfillShare,
      boolean overdue
  ) {
    List<BatchCloseSummaryResponse.BatchRedFlagResponse> persistedFlags = redFlagRepository.findAll().stream()
        .filter(flag -> !flag.isResolved() && batch.getId().equals(flag.getRelatedBatchId()))
        .map(flag -> toRedFlagResponse(flag, "system"))
        .toList();

    List<BatchCloseSummaryResponse.BatchRedFlagResponse> generatedFlags = new java.util.ArrayList<>();
    if (variancePercent.abs().compareTo(BigDecimal.valueOf(CLOSE_VARIANCE_THRESHOLD)) > 0) {
      generatedFlags.add(new BatchCloseSummaryResponse.BatchRedFlagResponse(
          "RF-S-" + batch.getId() + "-variance",
          batch.getId().toString(),
          "medium",
          "variance",
          "Variance " + variancePercent.stripTrailingZeros().toPlainString() + "% requires reviewer justification on close.",
          Instant.now(),
          null,
          null
      ));
    }
    if (landfillShare.compareTo(new BigDecimal("0.4")) > 0) {
      generatedFlags.add(new BatchCloseSummaryResponse.BatchRedFlagResponse(
          "RF-S-" + batch.getId() + "-landfill",
          batch.getId().toString(),
          "high",
          "landfill-risk",
          "Landfill share exceeds 40%. Batch score cap will be applied.",
          Instant.now(),
          null,
          null
      ));
    }
    if (overdue) {
      generatedFlags.add(new BatchCloseSummaryResponse.BatchRedFlagResponse(
          "RF-S-" + batch.getId() + "-overdue",
          batch.getId().toString(),
          "medium",
          "overdue-close",
          "Batch close is overdue and will reduce confidence.",
          Instant.now(),
          null,
          null
      ));
    }
    generatedFlags.addAll(persistedFlags);
    return generatedFlags;
  }

  private BatchCloseSummaryResponse.BatchRedFlagResponse toRedFlagResponse(RedFlagEntity flag, String resolvedBy) {
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

  private BatchResponse toBatchResponse(BatchEntity batch) {
    return new BatchResponse(
        batch.getId().toString(),
        batch.getTemplateName(),
        batch.getStartedAt(),
        batch.getOutputUnits(),
        batch.getWasteKg(),
        batch.getStatus().name(),
        batch.getClosedAt(),
        batch.getClosedBy(),
        batch.getCloseReason()
    );
  }

  private InventoryLogResponse toInventoryLogResponse(InventoryLogEntity entity) {
    return new InventoryLogResponse(
        entity.getId().toString(),
        entity.getBatch() == null ? null : entity.getBatch().getId().toString(),
        entity.getMaterialName(),
        entity.getType().name(),
        entity.getQuantity(),
        entity.getUnit(),
        entity.getSource(),
        entity.getTimestamp()
    );
  }

  private WasteLogResponse toWasteLogResponse(WasteLogEntity entity) {
    RecoveryMeta meta = recoveryMeta.get(entity.getId());
    return new WasteLogResponse(
        entity.getId().toString(),
        entity.getBatch().getId().toString(),
        entity.getMaterialName(),
        entity.getQuantityKg(),
        entity.getDestination().name(),
        "Operator input",
        normalizeRecoveryStatus(entity.getRecoveryStatus()),
        meta == null ? null : meta.inventoryLogId().toString(),
        meta == null ? null : meta.recoveredAt(),
        entity.getAiSuggestedAction(),
        entity.getRecoveryStatus() == RecoveryStatus.converted,
        entity.getTimestamp()
    );
  }

  private String normalizeRecoveryStatus(RecoveryStatus status) {
    return status == RecoveryStatus.not_applicable ? "not-applicable" : status.name();
  }

  private String resolveAiSuggestion(CreateWasteLogRequest request, WasteDestination destination) {
    if (request.aiSuggestedAction() != null && !request.aiSuggestedAction().isBlank()) {
      return request.aiSuggestedAction().trim();
    }
    return switch (destination) {
      case reuse -> "Route this material for immediate reuse in secondary production.";
      case repair -> "Send this material for repair and quality check before re-entry.";
      case dispose -> "Isolate and dispose according to compliance policy.";
    };
  }

  private BatchEntity resolveBatch(String batchId) {
    UUID id = parseUuid(batchId, "batchId");
    return batchRepository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));
  }

  private UUID parseUuid(String value, String fieldName) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UUID", "Invalid " + fieldName + ".");
    }
  }

  private InventoryType parseInventoryType(String type) {
    try {
      return InventoryType.valueOf(type.trim().toUpperCase());
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_TYPE", "Inventory type is invalid.");
    }
  }

  private WasteDestination parseDestination(String destination) {
    try {
      return WasteDestination.valueOf(destination.trim().toLowerCase());
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WASTE_DESTINATION", "Waste destination is invalid.");
    }
  }

  private void logActivity(
      EntityType entity,
      String entityId,
      String action,
      String batchId,
      String source,
      String details
  ) {
    ActivityLogEntity log = new ActivityLogEntity();
    log.setEntity(entity);
    log.setEntityId(entityId);
    log.setAction(action);
    log.setActor(resolveActor());
    String normalizedDetails = (batchId == null ? "" : "[batch:" + batchId + "] ") + "[" + source + "] " + details;
    log.setDetail(normalizedDetails.length() > 490 ? normalizedDetails.substring(0, 490) : normalizedDetails);
    log.setTimestamp(Instant.now());
    activityLogRepository.save(log);
  }

  private void logAudit(
      EntityType entity,
      String entityId,
      String field,
      String oldValue,
      String newValue,
      String reason
  ) {
    AuditTrailEntity trail = new AuditTrailEntity();
    trail.setEntity(entity);
    trail.setEntityId(entityId);
    trail.setField(field);
    trail.setOldValue(oldValue);
    trail.setNewValue(newValue);
    String payload = reason == null ? "" : "reason=" + reason;
    trail.setActor(resolveActor() + (payload.isBlank() ? "" : "|" + payload));
    trail.setTimestamp(Instant.now());
    auditTrailRepository.save(trail);
  }

  private String resolveActor() {
    String raw = SecurityUtils.getCurrentUserId();
    if (raw == null || raw.isBlank()) {
      return "operator.session";
    }
    return raw;
  }

  private BigDecimal scale(BigDecimal value, int scale) {
    return value.setScale(scale, RoundingMode.HALF_UP);
  }
}


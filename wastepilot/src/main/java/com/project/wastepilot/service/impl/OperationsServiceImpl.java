package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.dto.operations.BatchResponse;
import com.project.wastepilot.domain.dto.operations.CloseBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateInventoryLogRequest;
import com.project.wastepilot.domain.dto.operations.CreateWasteLogRequest;
import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.operations.OperationsPayloadResponse;
import com.project.wastepilot.domain.dto.operations.RecoverWasteRequest;
import com.project.wastepilot.domain.dto.operations.FloorViewBatchResponse;
import com.project.wastepilot.domain.dto.operations.WasteLogResponse;
import com.project.wastepilot.domain.dto.operations.WasteRecoveryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
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
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.project.wastepilot.service.OperationsService;
import com.project.wastepilot.service.AnomalyDetectionService;
import com.project.wastepilot.service.InsightGenerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationsServiceImpl implements OperationsService {
  private static final BigDecimal ONE_THOUSANDTH = new BigDecimal("0.001");
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final BatchRepository batchRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final TemplateRepository templateRepository;
  private final ActivityLogRepository activityLogRepository;
  private final AuditChainService auditChainService;
  private final BatchMetricsService batchMetricsService;
  private final FloorViewService floorViewService;
  private final AnomalyDetectionService anomalyDetectionService;
  private final InsightGenerationService insightGenerationService;
  private final WastePilotThresholds thresholds;

  @Override
  @Transactional(readOnly = true)
  public OperationsPayloadResponse getOperationsPayload() {
    // Use findTop50 to bound the operations panel table — full history is available via the batch detail view.
    List<BatchResponse> batches = batchRepository.findTop50ByOrderByStartedAtDesc().stream()
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

  @Override
  @Transactional
  public BatchResponse createBatch(CreateBatchRequest request) {
    String templateName = request.templateName().trim();
    TemplateEntity template = templateRepository.findByNameIgnoreCase(templateName).orElse(null);
    BigDecimal plannedInputKg = template == null
        ? request.outputUnits().multiply(new BigDecimal("0.08")).max(new BigDecimal("12"))
        : template.getLines().stream()
            .map(line -> line.getQuantity())
            .reduce(ZERO, BigDecimal::add);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName(templateName);
    batch.setPlannedInputKg(plannedInputKg);
    batch.setStartedAt(Instant.now());
    batch.setOutputUnits(request.outputUnits());
    batch.setWasteKg(request.wasteKg());
    batch.setStatus(BatchStatus.running);
    BatchEntity saved = batchRepository.save(batch);
    logActivity(EntityType.batch, saved.getId().toString(), "batch_started", saved.getId().toString(), "manual",
        "Started using " + saved.getTemplateName() + ".");
    return toBatchResponse(saved);
  }

  @Override
  @Transactional
  public InventoryLogResponse createInventoryLog(CreateInventoryLogRequest request) {
    InventoryType type = parseInventoryType(request.type());
    BatchEntity batch = null;
    if (request.batchId() != null && !request.batchId().isBlank()) {
      UUID batchUuid = parseUuid(request.batchId(), "batchId");
      batch = batchRepository.lockById(batchUuid)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));
      if (batch.getStatus() != BatchStatus.running) {
        throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_CLOSED",
            "Cannot add inventory to a batch that is not running.");
      }
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

  @Override
  @Transactional
  public WasteLogResponse createWasteLog(CreateWasteLogRequest request) {
    UUID batchUuid = parseUuid(request.batchId(), "batchId");
    BatchEntity batch = batchRepository.lockById(batchUuid)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));
    if (batch.getStatus() != BatchStatus.running) {
      throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_CLOSED",
          "Cannot add waste to a batch that is not running.");
    }
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
    anomalyDetectionService.evaluateWasteLog(saved);
    return toWasteLogResponse(saved);
  }

  @Override
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

    Instant recoveredAt = Instant.now();
    wasteLog.setRecoveryStatus(RecoveryStatus.converted);
    wasteLog.setRecoveryInventoryLogId(savedInventory.getId());
    wasteLog.setRecoveredAt(recoveredAt);
    wasteLogRepository.save(wasteLog);

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

  @Override
  @Transactional(readOnly = true)
  public BatchCloseSummaryResponse getBatchCloseSummary(String batchId) {
    BatchEntity batch = resolveBatch(batchId);
    return batchMetricsService.buildBatchCloseSummary(batch, null);
  }

  @Override
  @Transactional
  public BatchCloseSummaryResponse closeBatch(CloseBatchRequest request) {
    UUID batchId = parseUuid(request.batchId(), "batchId");
    BatchEntity batch = batchRepository.lockById(batchId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));
    if (batch.getStatus() != BatchStatus.running) {
      throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_CLOSED", "This batch is already closed.");
    }

    BatchCloseSummaryResponse preview = batchMetricsService.buildBatchCloseSummary(batch, request.outputUnits());
    int varianceGate = thresholds.getCloseVarianceThresholdPct();
    if (preview.variancePercent().abs().compareTo(BigDecimal.valueOf(varianceGate)) > 0
        && (request.closeReason() == null || request.closeReason().trim().isEmpty())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "CLOSE_REASON_REQUIRED",
          "Variance above " + varianceGate + "% requires a close reason."
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
    
    insightGenerationService.evaluateAfterBatchClose(batch);
    
    return batchMetricsService.buildBatchCloseSummary(batch, batch.getOutputUnits());
  }

  // Extracted to avoid N+1 query overhead in PatternReview logic.
  @Override
  @Transactional(readOnly = true)
  public BigDecimal getBatchVariancePercent(String batchId) {
    BatchEntity batch = resolveBatch(batchId);
    return batchMetricsService.calculateVariancePercent(batch);
  }

  @Override
  @Transactional
  public BatchResponse updateBatchOutputUnits(String batchId, com.project.wastepilot.domain.dto.operations.UpdateOutputUnitsRequest request) {
    UUID id = parseUuid(batchId, "batchId");
    BatchEntity batch = batchRepository.lockById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found."));

    if (batch.getStatus() != BatchStatus.completed) {
      throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_COMPLETED", "Only completed batches can be corrected.");
    }
    if (request.reason() == null || request.reason().trim().length() < 10) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CORRECTION_REASON_REQUIRED", "A correction reason of at least 10 characters is required.");
    }

    BigDecimal previousOutput = batch.getOutputUnits();
    batch.setOutputUnits(request.outputUnits());
    batchRepository.save(batch);

    if (previousOutput.compareTo(request.outputUnits()) != 0) {
      logAudit(
          EntityType.batch,
          batch.getId().toString(),
          "outputUnits",
          previousOutput.stripTrailingZeros().toPlainString(),
          request.outputUnits().stripTrailingZeros().toPlainString(),
          request.reason().trim()
      );
      logActivity(EntityType.batch, batch.getId().toString(), "batch_corrected", batch.getId().toString(), "manual", request.reason().trim());
    }

    return toBatchResponse(batch);
  }

  @Override
  @Transactional(readOnly = true)
  public int getOverdueBatchClosuresCount() {
    return batchMetricsService.getOverdueBatchClosuresCount();
  }

  @Override
  @Transactional(readOnly = true)
  public int getOpenRedFlagsCount() {
    return batchMetricsService.getOpenRedFlagsCount();
  }

  @Override
  @Transactional(readOnly = true)
  public int getAverageConfidenceScoreForCompletedBatches() {
    return batchMetricsService.getAverageConfidenceScoreForCompletedBatches();
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
    return new WasteLogResponse(
        entity.getId().toString(),
        entity.getBatch().getId().toString(),
        entity.getMaterialName(),
        entity.getQuantityKg(),
        entity.getDestination().name(),
        "Operator input",
        normalizeRecoveryStatus(entity.getRecoveryStatus()),
        entity.getRecoveryInventoryLogId() == null ? null : entity.getRecoveryInventoryLogId().toString(),
        entity.getRecoveredAt(),
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


  @Override
  @Transactional(readOnly = true)
  public List<FloorViewBatchResponse> getFloorView() {
    return floorViewService.getFloorView();
  }

  @Override
  public BatchEntity resolveRunningBatch(String batchId) {
    BatchEntity batch = resolveBatch(batchId);
    if (batch.getStatus() != BatchStatus.running) {
      throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_RUNNING", "Batch is not in running status.");
    }
    return batch;
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
    trail.setActor(resolveActor());
    trail.setReason(reason == null || reason.isBlank() ? null : reason.trim());
    trail.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));
    auditChainService.append(trail);
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

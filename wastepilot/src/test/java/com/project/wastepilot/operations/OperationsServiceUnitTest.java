package com.project.wastepilot.operations;

import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnomalyDetectionService;
import com.project.wastepilot.service.InsightGenerationService;
import com.project.wastepilot.service.impl.OperationsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link OperationsServiceImpl}.
 * No Spring context. Tests complex variance, confidence, and audit integrity calculations.
 */
@ExtendWith(MockitoExtension.class)
class OperationsServiceUnitTest {

  @Mock private BatchRepository batchRepository;
  @Mock private InventoryLogRepository inventoryLogRepository;
  @Mock private WasteLogRepository wasteLogRepository;
  @Mock private TemplateRepository templateRepository;
  @Mock private ActivityLogRepository activityLogRepository;
  @Mock private AuditTrailRepository auditTrailRepository;
  @Mock private RedFlagRepository redFlagRepository;
  @Mock private AnomalyDetectionService anomalyDetectionService;
  @Mock private InsightGenerationService insightGenerationService;

  @InjectMocks
  private OperationsServiceImpl operationsService;

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private BatchEntity createBatch(String templateName, BigDecimal outputUnits) {
    BatchEntity b = new BatchEntity();
    b.setId(UUID.randomUUID());
    b.setTemplateName(templateName);
    b.setOutputUnits(outputUnits);
    b.setStatus(BatchStatus.running); // Using running so getBatchCloseSummary doesn't trip on completed checks if any
    b.setStartedAt(Instant.now().minusSeconds(3600));
    b.setWasteKg(BigDecimal.ZERO);
    return b;
  }

  private TemplateEntity createTemplate(String name, BigDecimal lineQty) {
    TemplateEntity t = new TemplateEntity();
    t.setName(name);
    TemplateLineEntity line = new TemplateLineEntity();
    line.setQuantity(lineQty);
    t.setLines(List.of(line));
    return t;
  }

  private InventoryLogEntity createInLog(BatchEntity batch, BigDecimal qty) {
    InventoryLogEntity log = new InventoryLogEntity();
    log.setId(UUID.randomUUID());
    log.setBatch(batch);
    log.setType(InventoryType.IN);
    log.setQuantity(qty);
    return log;
  }
  
  private AuditTrailEntity createAudit(String reason) {
    AuditTrailEntity audit = new AuditTrailEntity();
    audit.setId(UUID.randomUUID());
    audit.setReason(reason);
    return audit;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Variance Calculation Tests (Tested via getBatchVariancePercent)
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * Zero planned input.
   * If template line qty is 0, plannedInputKg = 0.
   * The logic guards `plannedInputKg.compareTo(ZERO) > 0` before dividing.
   * Variance should safely return 0 (BigDecimal.ZERO).
   */
  @Test
  void variance_zeroPlannedInput_returnsZero_noDivisionByZero() {
    BatchEntity batch = createBatch("T1", BigDecimal.TEN);
    TemplateEntity template = createTemplate("T1", BigDecimal.ZERO); // Planned = 0
    InventoryLogEntity inLog = createInLog(batch, BigDecimal.valueOf(50)); // Measured = 50
    
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(templateRepository.findByNameIgnoreCase("T1")).thenReturn(Optional.of(template));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(inLog));

    BigDecimal variance = operationsService.getBatchVariancePercent(batch.getId().toString());

    assertThat(variance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  /**
   * Normal case: measured = planned.
   * Planned = 100. Measured = 100.
   * Variance should be 0.
   */
  @Test
  void variance_measuredEqualsPlanned_returnsZero() {
    BatchEntity batch = createBatch("T2", BigDecimal.TEN);
    TemplateEntity template = createTemplate("T2", new BigDecimal("100"));
    InventoryLogEntity inLog = createInLog(batch, new BigDecimal("100"));
    
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(templateRepository.findByNameIgnoreCase("T2")).thenReturn(Optional.of(template));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(inLog));

    BigDecimal variance = operationsService.getBatchVariancePercent(batch.getId().toString());

    assertThat(variance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  /**
   * Measured > planned.
   * Planned = 100. Measured = 120.
   * Variance should be +20%.
   */
  @Test
  void variance_measuredGreaterThanPlanned_returnsPositiveVariance() {
    BatchEntity batch = createBatch("T3", BigDecimal.TEN);
    TemplateEntity template = createTemplate("T3", new BigDecimal("100"));
    InventoryLogEntity inLog = createInLog(batch, new BigDecimal("120"));
    
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(templateRepository.findByNameIgnoreCase("T3")).thenReturn(Optional.of(template));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(inLog));

    BigDecimal variance = operationsService.getBatchVariancePercent(batch.getId().toString());

    assertThat(variance).isEqualByComparingTo(new BigDecimal("20.00"));
  }

  /**
   * No inventory logs (hasRealInput = false).
   * It uses planned input as actual input.
   * Planned = 100. Actual = 100.
   * Variance should be 0.
   */
  @Test
  void variance_noInventoryLogs_usesPlannedAsActual_returnsZero() {
    BatchEntity batch = createBatch("T4", BigDecimal.TEN);
    TemplateEntity template = createTemplate("T4", new BigDecimal("100"));
    
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(templateRepository.findByNameIgnoreCase("T4")).thenReturn(Optional.of(template));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of()); // No logs

    BigDecimal variance = operationsService.getBatchVariancePercent(batch.getId().toString());

    assertThat(variance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Audit Integrity and Confidence Score Tests (Tested via getBatchCloseSummary)
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void auditIntegrity_zeroAuditEntries_returnsOne() {
    BatchEntity batch = createBatch("T1", BigDecimal.TEN);
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(auditTrailRepository.findByEntityIdIn(any())).thenReturn(List.of()); // 0 entries

    BatchCloseSummaryResponse response = operationsService.getBatchCloseSummary(batch.getId().toString());

    assertThat(response.confidenceBreakdown().auditIntegrity()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void auditIntegrity_allValidReasons_returnsOne() {
    BatchEntity batch = createBatch("T1", BigDecimal.TEN);
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    
    // 2 entries, both valid (>= 10 chars)
    List<AuditTrailEntity> entries = List.of(
        createAudit("Valid reason 1"),
        createAudit("Valid reason 2")
    );
    when(auditTrailRepository.findByEntityIdIn(any())).thenReturn(entries);

    BatchCloseSummaryResponse response = operationsService.getBatchCloseSummary(batch.getId().toString());

    assertThat(response.confidenceBreakdown().auditIntegrity()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void auditIntegrity_halfValidReasons_returnsHalf() {
    BatchEntity batch = createBatch("T1", BigDecimal.TEN);
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    
    // 2 entries: 1 valid, 1 invalid (< 10 chars)
    List<AuditTrailEntity> entries = List.of(
        createAudit("Valid reason 1"),
        createAudit("Too short")
    );
    when(auditTrailRepository.findByEntityIdIn(any())).thenReturn(entries);

    BatchCloseSummaryResponse response = operationsService.getBatchCloseSummary(batch.getId().toString());

    assertThat(response.confidenceBreakdown().auditIntegrity()).isEqualByComparingTo(new BigDecimal("0.500"));
  }

  @Test
  void auditIntegrity_allInvalidReasons_returnsZero() {
    BatchEntity batch = createBatch("T1", BigDecimal.TEN);
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    
    // 2 entries, both invalid
    List<AuditTrailEntity> entries = List.of(
        createAudit("Short"),
        createAudit(" ")
    );
    when(auditTrailRepository.findByEntityIdIn(any())).thenReturn(entries);

    BatchCloseSummaryResponse response = operationsService.getBatchCloseSummary(batch.getId().toString());

    assertThat(response.confidenceBreakdown().auditIntegrity()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}

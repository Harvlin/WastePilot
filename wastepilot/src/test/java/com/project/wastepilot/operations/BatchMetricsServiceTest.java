package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.impl.BatchMetricsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchMetricsServiceTest {
  @Mock private BatchRepository batchRepository;
  @Mock private InventoryLogRepository inventoryLogRepository;
  @Mock private WasteLogRepository wasteLogRepository;
  @Mock private AuditTrailRepository auditTrailRepository;
  @Mock private RedFlagRepository redFlagRepository;

  private BatchMetricsService metricsService;

  @BeforeEach
  void setUp() {
    metricsService = new BatchMetricsService(
        batchRepository,
        inventoryLogRepository,
        wasteLogRepository,
        auditTrailRepository,
        redFlagRepository,
        new WastePilotThresholds()
    );
  }

  @Test
  void varianceUsesTheBatchSnapshotRatherThanCurrentTemplateValues() {
    BatchEntity batch = createBatch(new BigDecimal("100"));
    InventoryLogEntity input = new InventoryLogEntity();
    input.setType(InventoryType.IN);
    input.setQuantity(new BigDecimal("120"));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(input));

    assertThat(metricsService.calculateVariancePercent(batch)).isEqualByComparingTo("20.00");
  }

  @Test
  void varianceIsZeroWhenMeasuredEqualsPlanned() {
    BatchEntity batch = createBatch(new BigDecimal("100"));
    InventoryLogEntity input = new InventoryLogEntity();
    input.setType(InventoryType.IN);
    input.setQuantity(new BigDecimal("100"));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(input));

    assertThat(metricsService.calculateVariancePercent(batch)).isEqualByComparingTo("0.00");
  }

  @Test
  void varianceIsZeroWhenPlannedInputIsZeroWithoutDividingByZero() {
    BatchEntity batch = createBatch(BigDecimal.ZERO);
    InventoryLogEntity input = new InventoryLogEntity();
    input.setType(InventoryType.IN);
    input.setQuantity(new BigDecimal("10"));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(input));

    assertThat(metricsService.calculateVariancePercent(batch)).isEqualByComparingTo("0");
  }

  @Test
  void varianceWithoutTrackedInputUsesTheNoInputSentinel() {
    BatchEntity batch = createBatch(new BigDecimal("100"));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of());

    assertThat(metricsService.calculateVariancePercent(batch)).isEqualByComparingTo("-100.00");
  }

  @ParameterizedTest
  @CsvSource({
      "104.99, 4.99",
      "105.00, 5.00",
      "105.01, 5.01"
  })
  void varianceIsCorrectAtAndAroundTheCloseGate(BigDecimal measuredInput, BigDecimal expectedVariance) {
    BatchEntity batch = createBatch(new BigDecimal("100"));
    InventoryLogEntity input = new InventoryLogEntity();
    input.setType(InventoryType.IN);
    input.setQuantity(measuredInput);
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of(input));

    assertThat(metricsService.calculateVariancePercent(batch)).isEqualByComparingTo(expectedVariance);
  }

  @Test
  void closeSummaryReportsThePlannedInputSnapshotWhenInputIsNotTracked() {
    BatchEntity batch = createBatch(new BigDecimal("75"));
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of());
    when(wasteLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of());
    when(auditTrailRepository.findByEntityIdIn(List.of(batch.getId().toString()))).thenReturn(List.of());
    when(redFlagRepository.findByRelatedBatchIdAndResolvedFalse(batch.getId())).thenReturn(List.of());

    BatchCloseSummaryResponse summary = metricsService.buildBatchCloseSummary(batch, null);

    assertThat(summary.plannedInputKg()).isEqualByComparingTo("75.000");
    assertThat(summary.actualInputKg()).isEqualByComparingTo("75.000");
    assertThat(summary.actualInputSource()).isEqualTo("not-tracked");
  }

  @ParameterizedTest
  @CsvSource({
      "0, 0, 1.000",
      "2, 2, 1.000",
      "2, 1, 0.500",
      "2, 0, 0.000"
  })
  void auditIntegrityIsTheShareOfAuditEntriesWithMeaningfulReasons(
      int totalEntries, int meaningfulEntries, BigDecimal expectedIntegrity) {
    BatchEntity batch = createBatch(new BigDecimal("100"));
    List<AuditTrailEntity> entries = new java.util.ArrayList<>();
    for (int i = 0; i < totalEntries; i++) {
      AuditTrailEntity entry = new AuditTrailEntity();
      entry.setReason(i < meaningfulEntries ? "Corrected after recount" : "short");
      entries.add(entry);
    }
    when(inventoryLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of());
    when(wasteLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId())).thenReturn(List.of());
    when(auditTrailRepository.findByEntityIdIn(List.of(batch.getId().toString()))).thenReturn(entries);
    when(redFlagRepository.findByRelatedBatchIdAndResolvedFalse(batch.getId())).thenReturn(List.of());

    BatchCloseSummaryResponse summary = metricsService.buildBatchCloseSummary(batch, null);

    assertThat(summary.confidenceBreakdown().auditIntegrity()).isEqualByComparingTo(expectedIntegrity);
  }

  private BatchEntity createBatch(BigDecimal plannedInputKg) {
    BatchEntity batch = new BatchEntity();
    batch.setId(UUID.randomUUID());
    batch.setTemplateName("Template A");
    batch.setPlannedInputKg(plannedInputKg);
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(BigDecimal.TEN);
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.running);
    return batch;
  }
}

package com.project.wastepilot.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.CircularScoreCalculator;
import com.project.wastepilot.service.impl.AnalyticsServiceImpl;
import com.project.wastepilot.service.impl.DashboardServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsScoreParityTest {
  @Mock private WasteLogRepository wasteLogRepository;
  @Mock private InventoryLogRepository inventoryLogRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private InsightRepository insightRepository;
  @Mock private AnomalyRepository anomalyRepository;

  @Test
  void dashboardAndAnalyticsScoresMatchForTheSameBatchData() {
    Instant timestamp = Instant.now();
    BatchEntity batch = new BatchEntity();
    batch.setId(UUID.randomUUID());
    batch.setTemplateName("Parity test");
    batch.setPlannedInputKg(new BigDecimal("20"));
    batch.setStartedAt(timestamp);
    batch.setClosedAt(timestamp);
    batch.setOutputUnits(BigDecimal.valueOf(100));
    batch.setStatus(BatchStatus.completed);

    List<WasteLogEntity> waste = List.of(
        wasteLog(batch, WasteDestination.reuse, "8", timestamp),
        wasteLog(batch, WasteDestination.dispose, "2", timestamp));
    InventoryLogEntity inventory = new InventoryLogEntity();
    inventory.setId(UUID.randomUUID());
    inventory.setBatch(batch);
    inventory.setType(InventoryType.IN);
    inventory.setQuantity(new BigDecimal("20"));
    inventory.setTimestamp(timestamp);

    when(wasteLogRepository.findByTimestampAfterOrderByTimestampDesc(any())).thenReturn(waste);
    when(inventoryLogRepository.findByTimestampAfterOrderByTimestampDesc(any())).thenReturn(List.of(inventory));
    when(wasteLogRepository.findByBatchIdInOrderByTimestampDesc(any())).thenReturn(waste);
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), eq(InventoryType.IN))).thenReturn(List.of(inventory));
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed)).thenReturn(List.of(batch));
    when(insightRepository.findTop3ByOrderByTimestampDesc()).thenReturn(List.of());
    when(anomalyRepository.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());

    CircularScoreCalculator calculator = new CircularScoreCalculator();
    DashboardServiceImpl dashboard = new DashboardServiceImpl(
        wasteLogRepository, inventoryLogRepository, insightRepository, anomalyRepository,
        calculator, new WastePilotThresholds());
    AnalyticsServiceImpl analytics = new AnalyticsServiceImpl(
        wasteLogRepository, inventoryLogRepository, batchRepository, calculator);

    double dashboardScore = dashboard.getDashboardPayload().circularScore();
    BigDecimal analyticsScore = analytics.getAnalyticsPayload().circularityTrend().get(0).score();

    assertThat(dashboardScore).isEqualTo(analyticsScore.doubleValue());
  }

  private WasteLogEntity wasteLog(BatchEntity batch, WasteDestination destination, String quantity, Instant timestamp) {
    WasteLogEntity log = new WasteLogEntity();
    log.setId(UUID.randomUUID());
    log.setBatch(batch);
    log.setMaterialName("Cotton");
    log.setDestination(destination);
    log.setQuantityKg(new BigDecimal(quantity));
    log.setTimestamp(timestamp);
    return log;
  }
}

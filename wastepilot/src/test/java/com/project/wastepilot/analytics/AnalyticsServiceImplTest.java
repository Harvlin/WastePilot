package com.project.wastepilot.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.analytics.CircularityPoint;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.CircularScoreCalculator;
import com.project.wastepilot.service.impl.AnalyticsServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {
  @Mock
  private WasteLogRepository wasteLogRepository;
  @Mock
  private InventoryLogRepository inventoryLogRepository;
  @Mock
  private BatchRepository batchRepository;
  @Spy
  private CircularScoreCalculator circularScoreCalculator = new CircularScoreCalculator();
  @InjectMocks
  private AnalyticsServiceImpl analyticsService;

  @Test
  void trendUsesFiveMostRecentlyClosedBatchesInChronologicalDisplayOrder() {
    List<BatchEntity> batches = new ArrayList<>();
    List<WasteLogEntity> logs = new ArrayList<>();
    Instant start = Instant.parse("2026-01-01T00:00:00Z");

    for (int index = 6; index >= 1; index--) {
      BatchEntity batch = new BatchEntity();
      batch.setId(UUID.randomUUID());
      batch.setTemplateName("Batch " + index);
      batch.setPlannedInputKg(BigDecimal.ZERO);
      batch.setStartedAt(start.plusSeconds(index));
      batch.setClosedAt(start.plusSeconds(index));
      batch.setOutputUnits(BigDecimal.valueOf(100));
      batch.setStatus(BatchStatus.completed);
      batches.add(batch);

      logs.add(wasteLog(batch, WasteDestination.reuse, index));
      logs.add(wasteLog(batch, WasteDestination.dispose, 10 - index));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed)).thenReturn(batches);
    when(wasteLogRepository.findByBatchIdInOrderByTimestampDesc(any())).thenReturn(logs);
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), eq(InventoryType.IN))).thenReturn(List.of());

    List<CircularityPoint> trend = analyticsService.getAnalyticsPayload().circularityTrend();

    assertThat(trend).extracting(CircularityPoint::batchLabel).containsExactly("Batch 1", "Batch 2", "Batch 3", "Batch 4", "Batch 5");
    assertThat(trend).extracting(CircularityPoint::score)
        .containsExactly(
            new BigDecimal("15.0"),
            new BigDecimal("22.5"),
            new BigDecimal("30.0"),
            new BigDecimal("37.5"),
            new BigDecimal("45.0"));
  }

  @Test
  void batchLabelsKeepTheExistingAnalyticsWireProperty() throws Exception {
    String json = new ObjectMapper().writeValueAsString(new CircularityPoint("Batch 1", BigDecimal.ONE));

    assertThat(json).contains("\"week\":\"Batch 1\"").doesNotContain("batchLabel");
  }

  private WasteLogEntity wasteLog(BatchEntity batch, WasteDestination destination, int quantity) {
    WasteLogEntity log = new WasteLogEntity();
    log.setId(UUID.randomUUID());
    log.setBatch(batch);
    log.setMaterialName("Cotton");
    log.setDestination(destination);
    log.setQuantityKg(BigDecimal.valueOf(quantity));
    log.setTimestamp(batch.getClosedAt());
    return log;
  }
}

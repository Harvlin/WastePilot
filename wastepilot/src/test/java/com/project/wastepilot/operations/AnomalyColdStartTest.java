package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.impl.AnomalyDetectionServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnomalyColdStartTest {
  @Mock
  private WasteLogRepository wasteLogRepository;
  @Mock
  private AnomalyRepository anomalyRepository;

  @Test
  void createsExplicitProvisionalAnomalyAtConfiguredThresholdWithoutZScore() {
    WasteLogEntity log = wasteLog(new BigDecimal("10.000"));
    when(wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
        .thenReturn(List.of(log));
    when(anomalyRepository.existsByBatchIdAndMaterialNameAndDateAndStatus(
        any(), any(), any(), any())).thenReturn(false);
    AnomalyDetectionServiceImpl service = new AnomalyDetectionServiceImpl(
        wasteLogRepository, anomalyRepository, new BigDecimal("10"), new WastePilotThresholds());

    service.evaluateWasteLog(log);

    ArgumentCaptor<AnomalyEntity> captor = ArgumentCaptor.forClass(AnomalyEntity.class);
    verify(anomalyRepository).save(captor.capture());
    assertThat(captor.getValue().getDetectionMode()).isEqualTo("PROVISIONAL_THRESHOLD");
    assertThat(captor.getValue().getZScore()).isNull();
    assertThat(captor.getValue().getProvisionalThresholdKg()).isEqualByComparingTo("10");
    assertThat(captor.getValue().getNote()).contains("Cold-start provisional threshold");
  }

  @Test
  void doesNotCreateProvisionalAnomalyBelowConfiguredThreshold() {
    WasteLogEntity log = wasteLog(new BigDecimal("9.999"));
    when(wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
        .thenReturn(List.of(log));
    AnomalyDetectionServiceImpl service = new AnomalyDetectionServiceImpl(
        wasteLogRepository, anomalyRepository, new BigDecimal("10"), new WastePilotThresholds());

    service.evaluateWasteLog(log);

    verify(anomalyRepository, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 5, 10, 50, 100})
  void statisticalAnomalyDetectionHandlesZScoresAcrossSampleSizes(int sampleSize) {
    List<WasteLogEntity> baseline = baseline(sampleSize);
    BigDecimal mean = baseline.stream().map(WasteLogEntity::getQuantityKg)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(sampleSize), 6, java.math.RoundingMode.HALF_UP);
    BigDecimal squaredDifferences = baseline.stream()
        .map(WasteLogEntity::getQuantityKg)
        .map(quantity -> quantity.subtract(mean).pow(2))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal variance = squaredDifferences.divide(
        BigDecimal.valueOf(sampleSize - 1), 6, java.math.RoundingMode.HALF_UP);
    BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

    WasteLogEntity belowThreshold = wasteLog(
        mean.add(standardDeviation.multiply(new BigDecimal("2.49"))).setScale(9, java.math.RoundingMode.HALF_UP));
    when(wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(baseline);
    AnomalyDetectionServiceImpl service = new AnomalyDetectionServiceImpl(
        wasteLogRepository, anomalyRepository, new BigDecimal("10"), new WastePilotThresholds());

    service.evaluateWasteLog(belowThreshold);
    verify(anomalyRepository, never()).save(any());

    WasteLogEntity aboveThreshold = wasteLog(
        mean.add(standardDeviation.multiply(new BigDecimal("2.51"))).setScale(9, java.math.RoundingMode.HALF_UP));
    when(wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(baseline);
    service.evaluateWasteLog(aboveThreshold);
    verify(anomalyRepository).save(any(AnomalyEntity.class));
  }

  @Test
  void exactStatisticalAnomalyThresholdDoesNotCreateAnAnomaly() {
    List<WasteLogEntity> baseline = List.of(
        wasteLog(new BigDecimal("1")),
        wasteLog(new BigDecimal("2")),
        wasteLog(new BigDecimal("3")));
    when(wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(baseline);
    AnomalyDetectionServiceImpl service = new AnomalyDetectionServiceImpl(
        wasteLogRepository, anomalyRepository, new BigDecimal("10"), new WastePilotThresholds());

    service.evaluateWasteLog(wasteLog(new BigDecimal("4.5")));

    verify(anomalyRepository, never()).save(any());
  }

  private List<WasteLogEntity> baseline(int sampleSize) {
    return java.util.stream.IntStream.range(0, sampleSize)
        .mapToObj(index -> wasteLog(BigDecimal.valueOf(10 + index % 3)))
        .toList();
  }

  private WasteLogEntity wasteLog(BigDecimal quantity) {
    BatchEntity batch = new BatchEntity();
    batch.setId(UUID.randomUUID());
    batch.setTemplateName("Test batch");
    batch.setPlannedInputKg(BigDecimal.ZERO);
    WasteLogEntity log = new WasteLogEntity();
    log.setId(UUID.randomUUID());
    log.setBatch(batch);
    log.setMaterialName("Cotton");
    log.setQuantityKg(quantity);
    log.setTimestamp(Instant.now());
    log.setDestination(com.project.wastepilot.domain.enums.WasteDestination.dispose);
    return log;
  }
}

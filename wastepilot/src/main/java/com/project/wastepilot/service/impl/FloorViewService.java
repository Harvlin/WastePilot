package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.operations.FloorViewBatchResponse;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.repository.BatchRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FloorViewService {
  private final BatchRepository batchRepository;
  private final BatchMetricsService batchMetricsService;
  private final WastePilotThresholds thresholds;

  @Transactional(readOnly = true)
  public List<FloorViewBatchResponse> getFloorView() {
    return batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.running).stream()
        .map(this::toResponse)
        .toList();
  }

  private FloorViewBatchResponse toResponse(BatchEntity batch) {
    long runningMinutes = Duration.between(batch.getStartedAt(), Instant.now()).toMinutes();
    BigDecimal variance = batchMetricsService.calculateVariancePercent(batch);
    BigDecimal absoluteVariance = variance.abs();
    BigDecimal greenBand = BigDecimal.valueOf(thresholds.getFloorViewGreenBandPct());
    BigDecimal closeBand = BigDecimal.valueOf(thresholds.getCloseVarianceThresholdPct());

    String health = absoluteVariance.compareTo(greenBand) < 0
        ? "green"
        : absoluteVariance.compareTo(closeBand) < 0 ? "amber" : "red";

    return new FloorViewBatchResponse(
        batch.getId().toString(),
        batch.getTemplateName(),
        batch.getStartedAt(),
        runningMinutes,
        variance,
        health
    );
  }
}

package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.dashboard.CircularMetricResponse;
import com.project.wastepilot.domain.dto.dashboard.DashboardPayloadResponse;
import com.project.wastepilot.domain.dto.dashboard.InsightPreviewResponse;
import com.project.wastepilot.domain.dto.dashboard.TopAnomalyResponse;
import com.project.wastepilot.domain.dto.dashboard.WasteTrendPointResponse;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.InsightEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.CircularScoreCalculator;
import com.project.wastepilot.service.DashboardService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

  private static final int TREND_DAYS = 7;
  private static final int MAX_INSIGHTS_PREVIEW = 3;
  /** Rolling window for the dashboard KPI aggregation — keeps the score meaningful for active operations. */
  private static final int DASHBOARD_WINDOW_DAYS = 90;

  private final WasteLogRepository wasteLogRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final InsightRepository insightRepository;
  private final AnomalyRepository anomalyRepository;
  private final CircularScoreCalculator circularScoreCalculator;
  private final WastePilotThresholds thresholds;

  @Override
  @Transactional(readOnly = true)
  public DashboardPayloadResponse getDashboardPayload() {
    // Use a 90-day rolling window instead of loading all-time records into memory.
    Instant cutoff90 = Instant.now().minusSeconds(60L * 60 * 24 * DASHBOARD_WINDOW_DAYS);
    List<WasteLogEntity> recentWaste = wasteLogRepository.findByTimestampAfterOrderByTimestampDesc(cutoff90);
    List<InventoryLogEntity> recentInventory = inventoryLogRepository.findByTimestampAfterOrderByTimestampDesc(cutoff90);

    double[] scoreComponents = computeScoreComponents(recentWaste, recentInventory);
    double circularScore = circularScoreCalculator.compute(
        scoreComponents[0], scoreComponents[1], scoreComponents[2], scoreComponents[3]);

    List<CircularMetricResponse> metrics = buildMetrics(scoreComponents, recentWaste);
    List<WasteTrendPointResponse> wasteTrend = buildWasteTrend(recentWaste, recentInventory);
    List<InsightPreviewResponse> insights = buildInsightPreview();
    TopAnomalyResponse topAnomaly = buildTopAnomaly();

    return new DashboardPayloadResponse(
        BigDecimal.valueOf(circularScore).setScale(1, RoundingMode.HALF_UP).doubleValue(),
        metrics,
        wasteTrend,
        insights,
        topAnomaly
    );
  }

  /**
   * Returns [reuseKg, disposeKg, totalWasteKg, materialInputKg].
   */
  private double[] computeScoreComponents(List<WasteLogEntity> waste, List<InventoryLogEntity> inventory) {
    double totalWaste = waste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double reuseKg = waste.stream()
        .filter(w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double disposeKg = waste.stream()
        .filter(w -> w.getDestination() == WasteDestination.dispose)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double materialInput = inventory.stream()
        .filter(i -> i.getType() == InventoryType.IN)
        .mapToDouble(i -> i.getQuantity().doubleValue()).sum();
    return new double[]{reuseKg, disposeKg, totalWaste, materialInput};
  }

  private List<CircularMetricResponse> buildMetrics(double[] components, List<WasteLogEntity> waste) {
    // components: [reuseKg, disposeKg, totalWasteKg, materialInputKg]
    double reuseKg    = components[0];
    double totalWaste = components[2];
    double inputKg    = components[3];
    double reuseRatePct = totalWaste > 0 ? round(reuseKg / totalWaste * 100, 1) : 0.0;
    return List.of(
        new CircularMetricResponse("m1", "Material Input", round(inputKg, 1), "kg", 0.0),
        new CircularMetricResponse("m2", "Waste Output", round(totalWaste, 1), "kg", 0.0),
        new CircularMetricResponse("m3", "Recovery Rate", reuseRatePct, "%", 0.0)
    );
  }

  private List<WasteTrendPointResponse> buildWasteTrend(
      List<WasteLogEntity> recentWaste, List<InventoryLogEntity> recentInventory) {
    Instant now = Instant.now();
    Instant trendCutoff = now.minusSeconds(60L * 60 * 24 * TREND_DAYS);

    Map<String, List<WasteLogEntity>> wasteByDay = recentWaste.stream()
        .filter(w -> w.getTimestamp().isAfter(trendCutoff))
        .collect(Collectors.groupingBy(w -> dayLabel(w.getTimestamp())));

    Map<String, List<InventoryLogEntity>> inventoryByDay = recentInventory.stream()
        .filter(i -> i.getTimestamp().isAfter(trendCutoff) && i.getType() == InventoryType.IN)
        .collect(Collectors.groupingBy(i -> dayLabel(i.getTimestamp())));

    List<WasteTrendPointResponse> trend = new ArrayList<>();
    for (int dayOffset = TREND_DAYS - 1; dayOffset >= 0; dayOffset--) {
      Instant dayInstant = now.minusSeconds(60L * 60 * 24 * dayOffset);
      String label = dayLabel(dayInstant);
      List<WasteLogEntity> dayWaste = wasteByDay.getOrDefault(label, Collections.emptyList());
      List<InventoryLogEntity> dayInv = inventoryByDay.getOrDefault(label, Collections.emptyList());
      double input = dayInv.stream().mapToDouble(i -> i.getQuantity().doubleValue()).sum();
      double totalWaste = dayWaste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
      double reused = dayWaste.stream()
          .filter(w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair)
          .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
      trend.add(new WasteTrendPointResponse(label, round(input, 1), round(totalWaste, 1), round(reused, 1)));
    }

    boolean hasAnyData = trend.stream().anyMatch(t -> t.input() > 0 || t.waste() > 0);
    return hasAnyData ? trend : Collections.emptyList();
  }

  private List<InsightPreviewResponse> buildInsightPreview() {
    return insightRepository.findTop3ByOrderByTimestampDesc().stream()
        .map(this::toInsightPreview)
        .toList();
  }

  private InsightPreviewResponse toInsightPreview(InsightEntity entity) {
    return new InsightPreviewResponse(
        entity.getId().toString(),
        entity.getTitle(),
        entity.getContent(),
        entity.getImpactCategory(),
        entity.getStatus().toValue(),
        entity.getTimestamp()
    );
  }

  private TopAnomalyResponse buildTopAnomaly() {
    return anomalyRepository.findTopByOrderByTimestampDesc()
        .map(this::toTopAnomaly)
        .orElse(null);
  }

  private TopAnomalyResponse toTopAnomaly(AnomalyEntity entity) {
    return new TopAnomalyResponse(
        entity.getId().toString(),
        entity.getDate(),
        entity.getProcess(),
        entity.getZScore(),
        entity.getDetectionMode(),
        entity.getProvisionalThresholdKg(),
        entity.getWasteKg(),
        entity.getStatus().toValue(),
        entity.getNote()
    );
  }

  private String dayLabel(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
  }

  private double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }
}

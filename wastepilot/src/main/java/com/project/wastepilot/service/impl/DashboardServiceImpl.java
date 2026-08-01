package com.project.wastepilot.service.impl;

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

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final int TREND_DAYS = 7;
  private static final int MAX_INSIGHTS_PREVIEW = 3;

  private final WasteLogRepository wasteLogRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final InsightRepository insightRepository;
  private final AnomalyRepository anomalyRepository;

  @Override
  @Transactional(readOnly = true)
  public DashboardPayloadResponse getDashboardPayload() {
    List<WasteLogEntity> allWaste = wasteLogRepository.findAllByOrderByTimestampDesc();
    List<InventoryLogEntity> allInventory = inventoryLogRepository.findAllByOrderByTimestampDesc();

    CircularScore score = computeCircularScore(allWaste, allInventory);
    List<CircularMetricResponse> metrics = buildMetrics(score, allInventory, allWaste);
    List<WasteTrendPointResponse> wasteTrend = buildWasteTrend(allWaste, allInventory);
    List<InsightPreviewResponse> insights = buildInsightPreview();
    TopAnomalyResponse topAnomaly = buildTopAnomaly();

    return new DashboardPayloadResponse(
        round(score.value(), 1),
        metrics,
        wasteTrend,
        insights,
        topAnomaly
    );
  }

  // ── Circular Score ──────────────────────────────────────────────────────────

  private record CircularScore(double value, double materialInput, double totalWaste, double reuseRatePercent, double landfillSharePercent) {}

  private CircularScore computeCircularScore(List<WasteLogEntity> waste, List<InventoryLogEntity> inventory) {
    double totalWaste = waste.stream()
        .mapToDouble(w -> w.getQuantityKg().doubleValue())
        .sum();
    double safeWaste = Math.max(totalWaste, 0.0001);

    double reuseKg = waste.stream()
        .filter(w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair)
        .mapToDouble(w -> w.getQuantityKg().doubleValue())
        .sum();
    double disposeKg = waste.stream()
        .filter(w -> w.getDestination() == WasteDestination.dispose)
        .mapToDouble(w -> w.getQuantityKg().doubleValue())
        .sum();

    double materialInput = inventory.stream()
            .filter(i -> i.getType() == InventoryType.IN)
            .mapToDouble(i -> i.getQuantity().doubleValue())
            .sum();

    double recoveryRate = clamp(reuseKg / safeWaste, 0, 1);
    double wasteEfficiency = clamp(1 - totalWaste / Math.max(materialInput, 0.0001), 0, 1);
    double landfillShare = clamp(disposeKg / safeWaste, 0, 1);
    double landfillAvoidance = 1 - landfillShare;

    double base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);
    double cap = resolveLandfillCap(landfillShare);
    double score = clamp(Math.min(base, cap), 0, 100);

    return new CircularScore(
        round(score, 1),
        round(materialInput, 1),
        round(totalWaste, 1),
        round(recoveryRate * 100, 1),
        round(landfillShare * 100, 1)
    );
  }

  // ── Metrics Cards ───────────────────────────────────────────────────────────

  private List<CircularMetricResponse> buildMetrics(CircularScore score, List<InventoryLogEntity> inventory, List<WasteLogEntity> waste) {
    return List.of(
        new CircularMetricResponse("m1", "Material Input", score.materialInput(), "kg", 0.0),
        new CircularMetricResponse("m2", "Waste Output", score.totalWaste(), "kg", 0.0),
        new CircularMetricResponse("m3", "Recovery Rate", score.reuseRatePercent(), "%", 0.0)
    );
  }

  // ── Waste Trend (last N days, grouped by day of week) ───────────────────────

  private List<WasteTrendPointResponse> buildWasteTrend(List<WasteLogEntity> allWaste, List<InventoryLogEntity> allInventory) {
    Instant now = Instant.now();
    Instant cutoff = now.minusSeconds(60L * 60 * 24 * TREND_DAYS);

    // Group waste by day-of-week label
    Map<String, List<WasteLogEntity>> wasteByDay = allWaste.stream()
        .filter(w -> w.getTimestamp().isAfter(cutoff))
        .collect(Collectors.groupingBy(w -> dayLabel(w.getTimestamp())));

    Map<String, List<InventoryLogEntity>> inventoryByDay = allInventory.stream()
        .filter(i -> i.getTimestamp().isAfter(cutoff) && i.getType() == InventoryType.IN)
        .collect(Collectors.groupingBy(i -> dayLabel(i.getTimestamp())));

    // Walk the last 7 days in order: oldest → newest
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
          .mapToDouble(w -> w.getQuantityKg().doubleValue())
          .sum();

      trend.add(new WasteTrendPointResponse(label, round(input, 1), round(totalWaste, 1), round(reused, 1)));
    }

    // If no real data exists, return empty list; frontend will handle the fallback display
    boolean hasAnyData = trend.stream().anyMatch(t -> t.input() > 0 || t.waste() > 0);
    return hasAnyData ? trend : Collections.emptyList();
  }

  // ── Insights Preview ────────────────────────────────────────────────────────

  private List<InsightPreviewResponse> buildInsightPreview() {
    return insightRepository.findAllByOrderByTimestampDesc().stream()
        .limit(MAX_INSIGHTS_PREVIEW)
        .map(this::toInsightPreview)
        .toList();
  }

  private InsightPreviewResponse toInsightPreview(InsightEntity entity) {
    return new InsightPreviewResponse(
        entity.getId().toString(),
        entity.getTitle(),
        entity.getContent(),
        entity.getImpactCategory(),
        entity.getStatus().name(),
        entity.getTimestamp()
    );
  }

  // ── Top Anomaly ─────────────────────────────────────────────────────────────

  private TopAnomalyResponse buildTopAnomaly() {
    return anomalyRepository.findAllByOrderByTimestampDesc().stream()
        .findFirst()
        .map(this::toTopAnomaly)
        .orElse(null);
  }

  private TopAnomalyResponse toTopAnomaly(AnomalyEntity entity) {
    return new TopAnomalyResponse(
        entity.getId().toString(),
        entity.getDate(),
        entity.getProcess(),
        entity.getZScore(),
        entity.getWasteKg(),
        entity.getStatus().name(),
        entity.getNote()
    );
  }

  // ── Utility ─────────────────────────────────────────────────────────────────

  private String dayLabel(Instant instant) {
    return instant.atZone(ZoneOffset.UTC)
        .getDayOfWeek()
        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
  }

  private double resolveLandfillCap(double share) {
    if (share > 0.4) return 55;
    if (share > 0.3) return 70;
    if (share > 0.2) return 80;
    return 100;
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }
}

package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.analytics.AnalyticsPayloadResponse;
import com.project.wastepilot.domain.dto.analytics.CircularityPoint;
import com.project.wastepilot.domain.dto.analytics.EfficiencyItem;
import com.project.wastepilot.domain.dto.analytics.LandfillIntensityPoint;
import com.project.wastepilot.domain.dto.analytics.LandfillSharePoint;
import com.project.wastepilot.domain.dto.analytics.WasteBreakdownItem;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnalyticsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/**
 * ANALYTICS AUDIT NOTES (Minggu 3 Hardening Review)
 *
 * Classification of all calculations:
 *   (a) REAL — genuine aggregation from database records
 *   (b) DERIVED — computed metric from real data; formula documented inline
 *   (c) PLACEHOLDER — removed in this audit
 *
 * buildWasteBreakdown: (a) Real — sums actual WasteLog records by destination.
 * buildEfficiencyByMaterial: (b) Derived — efficiency = (1 - landfillShare) * 100. Reasonable proxy.
 * buildCircularityTrend (per-batch): (b) Derived — weighted formula, see computeBatchCircularScore.
 *   Formula weights: 40% recovery rate (reuse+repair / total waste), 60% landfill avoidance.
 *   These are policy-defined weights, not empirically measured; documented here for traceability.
 * buildLandfillShareTrend: (a) Real — per-batch landfill fraction from actual WasteLog records.
 * buildLandfillIntensityTrend: (b) Derived — kg-landfill per output-unit, from actual records.
 *
 * FIXED (Minggu 3): Removed all empty-state fallback stubs that returned a single synthetic
 * "W1" data point (including one with a hardcoded 0.030 value) when no completed batches existed.
 * Empty lists are now returned; the frontend should render an appropriate no-data state.
 */
public class AnalyticsServiceImpl implements AnalyticsService {

  private static final int WEEK_COUNT = 5;
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final WasteLogRepository wasteLogRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final BatchRepository batchRepository;

  @Override
  @Transactional(readOnly = true)
  public AnalyticsPayloadResponse getAnalyticsPayload() {
    List<WasteLogEntity> allWaste = wasteLogRepository.findAllByOrderByTimestampDesc();
    List<InventoryLogEntity> allInventory = inventoryLogRepository.findAllByOrderByTimestampDesc();
    List<BatchEntity> completedBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);

    List<CircularityPoint> circularityTrend = buildCircularityTrend(completedBatches, allWaste, allInventory);
    List<WasteBreakdownItem> wasteBreakdown = buildWasteBreakdown(allWaste);
    List<EfficiencyItem> efficiencyByMaterial = buildEfficiencyByMaterial(allWaste, allInventory);
    List<LandfillSharePoint> landfillShareTrend = buildLandfillShareTrend(completedBatches, allWaste);
    List<LandfillIntensityPoint> landfillIntensityTrend = buildLandfillIntensityTrend(completedBatches, allWaste);

    return new AnalyticsPayloadResponse(
        circularityTrend,
        wasteBreakdown,
        efficiencyByMaterial,
        landfillShareTrend,
        landfillIntensityTrend
    );
  }

  // ── Circularity Trend (per completed batch, latest N) ──────────────────────

  private List<CircularityPoint> buildCircularityTrend(
      List<BatchEntity> completedBatches,
      List<WasteLogEntity> allWaste,
      List<InventoryLogEntity> allInventory
  ) {
    List<BatchEntity> samples = completedBatches.stream()
        .sorted(Comparator.comparing(BatchEntity::getClosedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(WEEK_COUNT)
        .toList();

    if (samples.isEmpty()) {
      // No completed batches — return empty list; frontend renders no-data state.
      return List.of();
    }

    List<CircularityPoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      BatchEntity batch = samples.get(i);
      List<WasteLogEntity> batchWaste = allWaste.stream()
          .filter(w -> batch.getId().equals(w.getBatch().getId()))
          .toList();
      double score = computeBatchCircularScore(batchWaste);
      points.add(new CircularityPoint("W" + (i + 1), scale(score, 1)));
    }
    return points;
  }

  // ── Waste Breakdown (percentage by destination) ────────────────────────────

  private List<WasteBreakdownItem> buildWasteBreakdown(List<WasteLogEntity> allWaste) {
    if (allWaste.isEmpty()) {
      return List.of(
          new WasteBreakdownItem("Reusable", BigDecimal.ZERO),
          new WasteBreakdownItem("Repair", BigDecimal.ZERO),
          new WasteBreakdownItem("Landfill", BigDecimal.ZERO)
      );
    }

    double total = Math.max(0.0001, allWaste.stream()
        .mapToDouble(w -> w.getQuantityKg().doubleValue())
        .sum());

    double reuseKg = sumByDestination(allWaste, WasteDestination.reuse);
    double repairKg = sumByDestination(allWaste, WasteDestination.repair);
    double disposeKg = sumByDestination(allWaste, WasteDestination.dispose);

    return List.of(
        new WasteBreakdownItem("Reusable", scale(reuseKg / total * 100, 1)),
        new WasteBreakdownItem("Repair", scale(repairKg / total * 100, 1)),
        new WasteBreakdownItem("Landfill", scale(disposeKg / total * 100, 1))
    );
  }

  // ── Efficiency by Material (recovery efficiency per distinct material) ──────

  private List<EfficiencyItem> buildEfficiencyByMaterial(
      List<WasteLogEntity> allWaste,
      List<InventoryLogEntity> allInventory
  ) {
    // Group waste by material name
    Map<String, List<WasteLogEntity>> byMaterial = allWaste.stream()
        .collect(Collectors.groupingBy(WasteLogEntity::getMaterialName));

    if (byMaterial.isEmpty()) {
      return List.of();
    }

    return byMaterial.entrySet().stream()
        .map(entry -> {
          String material = entry.getKey();
          List<WasteLogEntity> logs = entry.getValue();

          double total = Math.max(0.0001, logs.stream()
              .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
          double disposed = logs.stream()
              .filter(w -> w.getDestination() == WasteDestination.dispose)
              .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();

          // Efficiency = 100% - landfill share
          double efficiency = clamp((1.0 - disposed / total) * 100, 0, 100);
          return new EfficiencyItem(material, scale(efficiency, 1));
        })
        .sorted(Comparator.comparing(e -> e.material()))
        .limit(10)
        .toList();
  }

  // ── Landfill Share Trend (per completed batch) ─────────────────────────────

  private List<LandfillSharePoint> buildLandfillShareTrend(
      List<BatchEntity> completedBatches,
      List<WasteLogEntity> allWaste
  ) {
    List<BatchEntity> samples = completedBatches.stream()
        .sorted(Comparator.comparing(BatchEntity::getClosedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(WEEK_COUNT)
        .toList();

    if (samples.isEmpty()) {
      // No completed batches — return empty list; frontend renders no-data state.
      return List.of();
    }

    List<LandfillSharePoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      BatchEntity batch = samples.get(i);
      List<WasteLogEntity> batchWaste = allWaste.stream()
          .filter(w -> batch.getId().equals(w.getBatch().getId()))
          .toList();
      double share = computeLandfillShare(batchWaste) * 100;
      points.add(new LandfillSharePoint("W" + (i + 1), scale(share, 1)));
    }
    return points;
  }

  // ── Landfill Intensity Trend (kg per output unit, per completed batch) ──────

  private List<LandfillIntensityPoint> buildLandfillIntensityTrend(
      List<BatchEntity> completedBatches,
      List<WasteLogEntity> allWaste
  ) {
    List<BatchEntity> samples = completedBatches.stream()
        .sorted(Comparator.comparing(BatchEntity::getClosedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(WEEK_COUNT)
        .toList();

    if (samples.isEmpty()) {
      // No completed batches — return empty list; frontend renders no-data state.
      // Previously returned a hardcoded 0.030 stub value, removed in Minggu 3 audit.
      return List.of();
    }

    List<LandfillIntensityPoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      BatchEntity batch = samples.get(i);
      List<WasteLogEntity> batchWaste = allWaste.stream()
          .filter(w -> batch.getId().equals(w.getBatch().getId()))
          .toList();

      double disposeKg = sumByDestination(batchWaste, WasteDestination.dispose);
      double outputUnits = batch.getOutputUnits() == null ? 0 : batch.getOutputUnits().doubleValue();
      double intensity = outputUnits > 0 ? disposeKg / outputUnits : disposeKg;
      points.add(new LandfillIntensityPoint("W" + (i + 1), scale(intensity, 3)));
    }
    return points;
  }

  // ── Score helpers ────────────────────────────────────────────────────────────

  private double computeBatchCircularScore(List<WasteLogEntity> batchWaste) {
    double total = Math.max(0.0001, batchWaste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
    double reuseKg = batchWaste.stream()
        .filter(w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double disposeKg = sumByDestination(batchWaste, WasteDestination.dispose);

    double recoveryRate = clamp(reuseKg / total, 0, 1);
    double landfillShare = clamp(disposeKg / total, 0, 1);
    double landfillAvoidance = 1 - landfillShare;

    // Formula weights (policy-defined, not empirically measured):
    //   40% recovery rate (reuse+repair as fraction of total waste)
    //   60% landfill avoidance (1 - landfill fraction)
    double base = 100 * (0.4 * recoveryRate + 0.6 * landfillAvoidance);
    double cap = resolveLandfillCap(landfillShare);
    return clamp(Math.min(base, cap), 0, 100);
  }

  private double computeGlobalCircularScore(List<WasteLogEntity> allWaste, List<InventoryLogEntity> allInventory) {
    double total = Math.max(0.0001, allWaste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
    double reuseKg = allWaste.stream()
        .filter(w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double disposeKg = sumByDestination(allWaste, WasteDestination.dispose);
    double materialInput = Math.max(0.0001, allInventory.stream()
        .filter(i -> i.getType() == InventoryType.IN)
        .mapToDouble(i -> i.getQuantity().doubleValue()).sum());

    double recoveryRate = clamp(reuseKg / total, 0, 1);
    double wasteEfficiency = clamp(1 - total / materialInput, 0, 1);
    double landfillShare = clamp(disposeKg / total, 0, 1);
    double landfillAvoidance = 1 - landfillShare;

    double base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);
    double cap = resolveLandfillCap(landfillShare);
    return clamp(Math.min(base, cap), 0, 100);
  }

  private double computeGlobalLandfillShare(List<WasteLogEntity> allWaste) {
    return allWaste.isEmpty() ? 0.0 : computeLandfillShare(allWaste);
  }

  private double computeLandfillShare(List<WasteLogEntity> waste) {
    double total = Math.max(0.0001, waste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
    double disposed = sumByDestination(waste, WasteDestination.dispose);
    return clamp(disposed / total, 0, 1);
  }

  // ── Utility ─────────────────────────────────────────────────────────────────

  private double sumByDestination(List<WasteLogEntity> waste, WasteDestination destination) {
    return waste.stream()
        .filter(w -> w.getDestination() == destination)
        .mapToDouble(w -> w.getQuantityKg().doubleValue())
        .sum();
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

  private BigDecimal scale(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
  }
}

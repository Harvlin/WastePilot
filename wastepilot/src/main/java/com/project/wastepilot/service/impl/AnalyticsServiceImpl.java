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
import com.project.wastepilot.service.CircularScoreCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

  private static final int BATCH_COUNT = 5;

  private final WasteLogRepository wasteLogRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final BatchRepository batchRepository;
  private final CircularScoreCalculator circularScoreCalculator;

  @Override
  @Transactional(readOnly = true)
  public AnalyticsPayloadResponse getAnalyticsPayload() {
    // Fetch the specific completed batches we need for trend charts (capped at BATCH_COUNT).
    List<BatchEntity> completedBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);
    List<BatchEntity> trendSamples = latestCompletedBatches(completedBatches);

    // Batch-fetch waste and inventory logs for just those batches — no unbounded all-table load.
    // For breakdown and efficiency charts we use the global all-time data (bounded by completed batches).
    List<WasteLogEntity> allWaste;
    List<InventoryLogEntity> allInventoryIn;
    if (trendSamples.isEmpty()) {
      allWaste = List.of();
      allInventoryIn = List.of();
    } else {
      Set<UUID> allCompletedIds = completedBatches.stream().map(BatchEntity::getId).collect(Collectors.toSet());
      allWaste = wasteLogRepository.findByBatchIdInOrderByTimestampDesc(allCompletedIds);
      allInventoryIn = inventoryLogRepository.findByBatch_IdInAndType(allCompletedIds, InventoryType.IN);
    }

    // Build maps keyed by batch ID for O(1) lookup inside trend loops.
    Map<UUID, List<WasteLogEntity>> wasteByBatch = allWaste.stream()
        .collect(Collectors.groupingBy(w -> w.getBatch().getId()));
    Map<UUID, List<InventoryLogEntity>> inventoryByBatch = allInventoryIn.stream()
        .collect(Collectors.groupingBy(i -> i.getBatch().getId()));

    List<CircularityPoint>        circularityTrend     = buildCircularityTrend(trendSamples, wasteByBatch, inventoryByBatch);
    List<WasteBreakdownItem>      wasteBreakdown       = buildWasteBreakdown(allWaste);
    List<EfficiencyItem>          efficiencyByMaterial = buildEfficiencyByMaterial(allWaste);
    List<LandfillSharePoint>      landfillShareTrend   = buildLandfillShareTrend(trendSamples, wasteByBatch);
    List<LandfillIntensityPoint>  landfillIntensity    = buildLandfillIntensityTrend(trendSamples, wasteByBatch);

    return new AnalyticsPayloadResponse(
        circularityTrend,
        wasteBreakdown,
        efficiencyByMaterial,
        landfillShareTrend,
        landfillIntensity
    );
  }


  private List<CircularityPoint> buildCircularityTrend(
      List<BatchEntity> samples,
      Map<UUID, List<WasteLogEntity>> wasteByBatch,
      Map<UUID, List<InventoryLogEntity>> inventoryByBatch
  ) {
    if (samples.isEmpty()) return List.of();

    List<CircularityPoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      BatchEntity batch = samples.get(i);
      List<WasteLogEntity> batchWaste = wasteByBatch.getOrDefault(batch.getId(), List.of());
      List<InventoryLogEntity> batchInv = inventoryByBatch.getOrDefault(batch.getId(), List.of());

      double score = circularScoreCalculator.computeFromLogs(
          batchWaste,
          w -> w.getQuantityKg().doubleValue(),
          w -> w.getDestination() == WasteDestination.reuse || w.getDestination() == WasteDestination.repair,
          w -> w.getDestination() == WasteDestination.dispose,
          batchInv,
          i2 -> i2.getQuantity().doubleValue()
      );
      points.add(new CircularityPoint("Batch " + (i + 1), scale(score, 1)));
    }
    return points;
  }


  private List<WasteBreakdownItem> buildWasteBreakdown(List<WasteLogEntity> allWaste) {
    if (allWaste.isEmpty()) {
      return List.of(
          new WasteBreakdownItem("Reusable", BigDecimal.ZERO),
          new WasteBreakdownItem("Repair",   BigDecimal.ZERO),
          new WasteBreakdownItem("Landfill", BigDecimal.ZERO)
      );
    }
    double total = Math.max(0.0001, allWaste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
    double reuseKg   = sumByDest(allWaste, WasteDestination.reuse);
    double repairKg  = sumByDest(allWaste, WasteDestination.repair);
    double disposeKg = sumByDest(allWaste, WasteDestination.dispose);
    return List.of(
        new WasteBreakdownItem("Reusable", scale(reuseKg   / total * 100, 1)),
        new WasteBreakdownItem("Repair",   scale(repairKg  / total * 100, 1)),
        new WasteBreakdownItem("Landfill", scale(disposeKg / total * 100, 1))
    );
  }


  private List<EfficiencyItem> buildEfficiencyByMaterial(List<WasteLogEntity> allWaste) {
    if (allWaste.isEmpty()) return List.of();

    Map<String, List<WasteLogEntity>> byMaterial = allWaste.stream()
        .collect(Collectors.groupingBy(WasteLogEntity::getMaterialName));

    return byMaterial.entrySet().stream()
        .map(entry -> {
          double total    = Math.max(0.0001, entry.getValue().stream()
              .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
          double disposed = entry.getValue().stream()
              .filter(w -> w.getDestination() == WasteDestination.dispose)
              .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
          double efficiency = clamp((1.0 - disposed / total) * 100, 0, 100);
          return new EfficiencyItem(entry.getKey(), scale(efficiency, 1));
        })
        .sorted(Comparator.comparing(EfficiencyItem::material))
        .limit(10)
        .toList();
  }


  private List<LandfillSharePoint> buildLandfillShareTrend(
      List<BatchEntity> samples,
      Map<UUID, List<WasteLogEntity>> wasteByBatch
  ) {
    if (samples.isEmpty()) return List.of();
    List<LandfillSharePoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      List<WasteLogEntity> batchWaste = wasteByBatch.getOrDefault(samples.get(i).getId(), List.of());
      double share = computeLandfillShare(batchWaste) * 100;
      points.add(new LandfillSharePoint("Batch " + (i + 1), scale(share, 1)));
    }
    return points;
  }


  private List<LandfillIntensityPoint> buildLandfillIntensityTrend(
      List<BatchEntity> samples,
      Map<UUID, List<WasteLogEntity>> wasteByBatch
  ) {
    if (samples.isEmpty()) return List.of();
    List<LandfillIntensityPoint> points = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      BatchEntity batch = samples.get(i);
      List<WasteLogEntity> batchWaste = wasteByBatch.getOrDefault(batch.getId(), List.of());
      double disposeKg   = sumByDest(batchWaste, WasteDestination.dispose);
      double outputUnits = batch.getOutputUnits() == null ? 0 : batch.getOutputUnits().doubleValue();
      double intensity   = outputUnits > 0 ? disposeKg / outputUnits : disposeKg;
      points.add(new LandfillIntensityPoint("Batch " + (i + 1), scale(intensity, 3)));
    }
    return points;
  }


  private List<BatchEntity> latestCompletedBatches(List<BatchEntity> completedBatches) {
    return completedBatches.stream()
        .sorted(Comparator.comparing(BatchEntity::getClosedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(BATCH_COUNT)
        .sorted(Comparator.comparing(BatchEntity::getClosedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private double computeLandfillShare(List<WasteLogEntity> waste) {
    double total    = Math.max(0.0001, waste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum());
    double disposed = sumByDest(waste, WasteDestination.dispose);
    return clamp(disposed / total, 0, 1);
  }

  private double sumByDest(List<WasteLogEntity> waste, WasteDestination dest) {
    return waste.stream().filter(w -> w.getDestination() == dest)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
  }

  private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

  private BigDecimal scale(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
  }
}

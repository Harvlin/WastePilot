package com.project.wastepilot.service;

import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the Circular Economy Score calculation.
 *
 * <p><b>Formula (3-term, policy-defined weights):</b>
 * <pre>
 *   recoveryRate      = clamp(reuseKg / totalWaste, 0, 1)
 *   wasteEfficiency   = clamp(1 - totalWaste / materialInput, 0, 1)
 *   landfillAvoidance = 1 - clamp(disposeKg / totalWaste, 0, 1)
 *
 *   base  = 100 × (WEIGHT_RECOVERY × recoveryRate
 *                + WEIGHT_EFFICIENCY × wasteEfficiency
 *                + WEIGHT_LANDFILL_AVOIDANCE × landfillAvoidance)
 *   score = clamp(min(base, landfillCap(landfillShare)), 0, 100)
 * </pre>
 *
 * <p>When applied per-batch and {@code materialInput} is zero (no inventory IN logs recorded),
 * {@code wasteEfficiency} degrades to 0.0, reflecting genuinely untracked input. This is the
 * correct behaviour — callers should surface this to users via the no-input-tracked flag.
 *
 * <p><b>Landfill cap (progressive penalty for high landfill share):</b>
 * <ul>
 *   <li>&gt;40% landfill → hard cap at 55</li>
 *   <li>&gt;30% landfill → hard cap at 70</li>
 *   <li>&gt;20% landfill → hard cap at 80</li>
 *   <li>≤20% landfill → no cap (max 100)</li>
 * </ul>
 */
@Component
public class CircularScoreCalculator {

  // --- Weights (must sum to 1.0) ---
  public static final double WEIGHT_RECOVERY           = 0.30;
  public static final double WEIGHT_EFFICIENCY         = 0.25;
  public static final double WEIGHT_LANDFILL_AVOIDANCE = 0.45;

  // --- Landfill cap thresholds ---
  public static final double LANDFILL_CAP_TIER_HIGH    = 0.40;   // share > 40% → cap 55
  public static final double LANDFILL_CAP_TIER_MID     = 0.30;   // share > 30% → cap 70
  public static final double LANDFILL_CAP_TIER_LOW     = 0.20;   // share > 20% → cap 80

  public static final double LANDFILL_CAP_HIGH         = 55.0;
  public static final double LANDFILL_CAP_MID          = 70.0;
  public static final double LANDFILL_CAP_LOW          = 80.0;
  public static final double LANDFILL_CAP_NONE         = 100.0;

  /**
   * Compute the Circular Economy Score from pre-aggregated kg totals.
   *
   * @param reuseKg       kg of waste destined for reuse or repair
   * @param disposeKg     kg of waste destined for landfill/dispose
   * @param totalWasteKg  total waste kg (reuseKg + repairKg + disposeKg)
   * @param materialInputKg total inventory-IN kg (may be 0 if not tracked)
   * @return score in [0, 100], rounded to 1 decimal place
   */
  public double compute(double reuseKg, double disposeKg, double totalWasteKg, double materialInputKg) {
    double safeWaste = Math.max(totalWasteKg, 0.0001);
    double safeInput = Math.max(materialInputKg, 0.0001);

    double recoveryRate      = clamp(reuseKg / safeWaste, 0, 1);
    double wasteEfficiency   = clamp(1.0 - totalWasteKg / safeInput, 0, 1);
    double landfillShare     = clamp(disposeKg / safeWaste, 0, 1);
    double landfillAvoidance = 1.0 - landfillShare;

    double base = 100.0 * (WEIGHT_RECOVERY * recoveryRate
                         + WEIGHT_EFFICIENCY * wasteEfficiency
                         + WEIGHT_LANDFILL_AVOIDANCE * landfillAvoidance);
    double cap  = resolveLandfillCap(landfillShare);
    return round1(clamp(Math.min(base, cap), 0, 100));
  }

  /**
   * Compute the score from a collection of raw entity objects.
   * Accepts functional interfaces so callers don't need to expose entity types here.
   */
  public <W, I> double computeFromLogs(
      Collection<W> wasteLogs,
      java.util.function.ToDoubleFunction<W> wasteKgFn,
      java.util.function.Predicate<W> isReuseFn,
      java.util.function.Predicate<W> isDisposeFn,
      Collection<I> inventoryInLogs,
      java.util.function.ToDoubleFunction<I> inventoryQtyFn
  ) {
    double totalWaste = wasteLogs.stream().mapToDouble(wasteKgFn).sum();
    double reuseKg    = wasteLogs.stream().filter(isReuseFn).mapToDouble(wasteKgFn).sum();
    double disposeKg  = wasteLogs.stream().filter(isDisposeFn).mapToDouble(wasteKgFn).sum();
    double inputKg    = inventoryInLogs.stream().mapToDouble(inventoryQtyFn).sum();
    return compute(reuseKg, disposeKg, totalWaste, inputKg);
  }

  public double resolveLandfillCap(double share) {
    if (share > LANDFILL_CAP_TIER_HIGH) return LANDFILL_CAP_HIGH;
    if (share > LANDFILL_CAP_TIER_MID)  return LANDFILL_CAP_MID;
    if (share > LANDFILL_CAP_TIER_LOW)  return LANDFILL_CAP_LOW;
    return LANDFILL_CAP_NONE;
  }

  private double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}

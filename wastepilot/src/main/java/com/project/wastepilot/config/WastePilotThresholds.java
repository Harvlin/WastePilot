package com.project.wastepilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized configuration for all operational thresholds that were previously scattered as
 * hardcoded constants across multiple service classes.
 *
 * <p>Values can be overridden per-environment via {@code application.properties} or env vars.
 * All defaults preserve exact existing behaviour — this is a consolidation only, no behaviour change.
 *
 * <h3>Threshold inventory (where each was previously defined):</h3>
 * <ul>
 *   <li>{@code anomalyZScoreThreshold} — was {@code ANOMALY_Z_SCORE_THRESHOLD = 2.5} in
 *       {@code AnomalyDetectionServiceImpl}</li>
 *   <li>{@code closeVarianceThresholdPct} — used as both the close-gate and floor-view red threshold
 *       by the operations and floor-view services</li>
 *   <li>{@code floorViewGreenBandPct} — green below this, amber between green and close-gate</li>
 *   <li>{@code crossValidationTolerancePct} — was {@code CROSS_VALIDATION_TOLERANCE = 15} in
 *       {@code IntegrityServiceImpl}</li>
 *   <li>{@code patternReviewWindow} — was {@code PATTERN_REVIEW_WINDOW = 20} in
 *       {@code IntegrityServiceImpl}</li>
 *   <li>{@code patternReviewMinSample} — was {@code PATTERN_REVIEW_MIN_SAMPLE = 5} in
 *       {@code IntegrityServiceImpl}</li>
 *   <li>{@code patternReviewSuspicionThreshold} — was {@code PATTERN_REVIEW_SUSPICION_THRESHOLD = 0.60}
 *       in {@code IntegrityServiceImpl}</li>
 *   <li>{@code patternReviewBandPoints} — was {@code PATTERN_REVIEW_BAND_POINTS = 0.5} in
 *       {@code IntegrityServiceImpl} (the ±0.5% band below the variance threshold used to detect
 *       "just-under-threshold" gaming)</li>
 * </ul>
 *
 * <h3>Discrepancy flagged during consolidation:</h3>
 * <p>No discrepancies found in the threshold values themselves — the 5% close-variance constant was
 * already unified (see prior audit fix). The floor-view green band (3%) was the only unlabelled
 * magic number; it is now named {@code floorViewGreenBandPct}.
 */
@Component
@ConfigurationProperties(prefix = "wastepilot.thresholds")
public class WastePilotThresholds {

  /**
   * Z-score above which a waste log is flagged as a statistical anomaly.
   * Default: 2.5 (matches the previous hardcoded constant).
   */
  private double anomalyZScoreThreshold = 2.5;

  /**
   * Variance percentage above which a batch close requires a written reason,
   * AND above which the floor-view health colour becomes red.
   * Default: 5 (percent).
   */
  private int closeVarianceThresholdPct = 5;

  /**
   * Floor-view variance below this is green; between this and closeVarianceThresholdPct is amber.
   * Default: 3 (percent).
   */
  private int floorViewGreenBandPct = 3;

  /**
   * Relative discrepancy (%) between manual and sensor readings required before flagging
   * a cross-validation discrepancy.
   * Default: 15 (percent).
   */
  private int crossValidationTolerancePct = 15;

  /**
   * Number of most-recent completed batches to examine per user in pattern review.
   * Default: 20.
   */
  private int patternReviewWindow = 20;

  /**
   * Minimum number of batches required before generating a pattern review flag.
   * Prevents false positives on new users.
   * Default: 5.
   */
  private int patternReviewMinSample = 5;

  /**
   * Fraction of batches that must fall within the suspicious band before flagging a user.
   * Default: 0.60 (60%).
   */
  private double patternReviewSuspicionThreshold = 0.60;

  /**
   * Width of the suspicious band below closeVarianceThresholdPct, in percentage points.
   * Batches with abs(variance) in [threshold - band, threshold] are considered suspiciously
   * close to the close gate.
   * Default: 0.5 (percentage points).
   */
  private double patternReviewBandPoints = 0.5;

  // --- Getters and setters (required by @ConfigurationProperties) ---

  public double getAnomalyZScoreThreshold() { return anomalyZScoreThreshold; }
  public void setAnomalyZScoreThreshold(double v) { this.anomalyZScoreThreshold = v; }

  public int getCloseVarianceThresholdPct() { return closeVarianceThresholdPct; }
  public void setCloseVarianceThresholdPct(int v) { this.closeVarianceThresholdPct = v; }

  public int getFloorViewGreenBandPct() { return floorViewGreenBandPct; }
  public void setFloorViewGreenBandPct(int v) { this.floorViewGreenBandPct = v; }

  public int getCrossValidationTolerancePct() { return crossValidationTolerancePct; }
  public void setCrossValidationTolerancePct(int v) { this.crossValidationTolerancePct = v; }

  public int getPatternReviewWindow() { return patternReviewWindow; }
  public void setPatternReviewWindow(int v) { this.patternReviewWindow = v; }

  public int getPatternReviewMinSample() { return patternReviewMinSample; }
  public void setPatternReviewMinSample(int v) { this.patternReviewMinSample = v; }

  public double getPatternReviewSuspicionThreshold() { return patternReviewSuspicionThreshold; }
  public void setPatternReviewSuspicionThreshold(double v) { this.patternReviewSuspicionThreshold = v; }

  public double getPatternReviewBandPoints() { return patternReviewBandPoints; }
  public void setPatternReviewBandPoints(double v) { this.patternReviewBandPoints = v; }
}

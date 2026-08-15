package com.project.wastepilot.integrity;

import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.dto.integrity.CrossValidationDiscrepancy;
import com.project.wastepilot.domain.dto.integrity.PatternReviewEntry;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.OperationsService;
import com.project.wastepilot.service.impl.IntegrityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link IntegrityServiceImpl}.
 * No Spring context — Mockito only. Tests Pattern Review and Cross-Validation logic.
 */
@ExtendWith(MockitoExtension.class)
class IntegrityServiceUnitTest {

  @Mock private ActivityLogRepository activityLogRepository;
  @Mock private AuditTrailRepository auditTrailRepository;
  @Mock private InventoryLogRepository inventoryLogRepository;
  @Mock private WasteLogRepository wasteLogRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private OperationsService operationsService;

  @InjectMocks
  private IntegrityServiceImpl integrityService;

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private static BatchEntity completedBatch(String closedBy) {
    BatchEntity b = new BatchEntity();
    b.setId(UUID.randomUUID());
    b.setTemplateName("T1");
    b.setStatus(BatchStatus.completed);
    b.setStartedAt(Instant.now().minusSeconds(3600));
    b.setClosedAt(Instant.now());
    b.setClosedBy(closedBy);
    b.setOutputUnits(BigDecimal.TEN);
    b.setWasteKg(BigDecimal.ZERO);
    return b;
  }

  /** Returns a BigDecimal variance that IS suspicious (within [4.5, 5.0] band). */
  private static BigDecimal suspiciousVariance() {
    return new BigDecimal("4.8");
  }

  /** Returns a BigDecimal variance that is NOT suspicious (clearly below band). */
  private static BigDecimal normalVariance() {
    return new BigDecimal("2.0");
  }

  private static InventoryLogEntity inventoryLog(BatchEntity batch, String material,
                                                   String source, String qty) {
    InventoryLogEntity log = new InventoryLogEntity();
    log.setId(UUID.randomUUID());
    log.setBatch(batch);
    log.setMaterialName(material);
    log.setType(InventoryType.IN);
    log.setSource(source);
    log.setQuantity(new BigDecimal(qty));
    log.setUnit("kg");
    log.setTimestamp(Instant.now());
    return log;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Pattern Review Tests
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * PATTERN_REVIEW_MIN_SAMPLE = 5.
   * User has exactly 5 batches, all suspicious → should be flagged.
   * suspiciousCount=5, total=5, ratio=1.0 >= 0.60 → flagged.
   */
  @Test
  void patternReview_exactlyAtMinSample_allSuspicious_isFlagged() {
    List<BatchEntity> batches = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      batches.add(completedBatch("alice"));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(batches);
    when(operationsService.getBatchVariancePercent(any()))
        .thenReturn(suspiciousVariance());

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).hasSize(1);
    PatternReviewEntry entry = results.get(0);
    assertThat(entry.closedBy()).isEqualTo("alice");
    assertThat(entry.suspiciousCloseCount()).isEqualTo(5);
    assertThat(entry.totalCloseCount()).isEqualTo(5);
  }

  /**
   * User has fewer than PATTERN_REVIEW_MIN_SAMPLE (5) batches.
   * Should NOT be flagged — insufficient sample to avoid false positives on new users.
   */
  @Test
  void patternReview_belowMinSample_notFlagged() {
    List<BatchEntity> batches = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      batches.add(completedBatch("bob"));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(batches);

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).isEmpty();
  }

  /**
   * PATTERN_REVIEW_WINDOW = 20.
   * User has 25 batches. Only the most recent 20 should be considered.
   * We make all 25 suspicious, but only 20 count.
   */
  @Test
  void patternReview_moreThanWindow_onlyWindowConsidered() {
    List<BatchEntity> batches = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      batches.add(completedBatch("charlie"));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(batches);
    // All suspicious
    when(operationsService.getBatchVariancePercent(any()))
        .thenReturn(suspiciousVariance());

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).hasSize(1);
    // Only 20 batches in the window should be reviewed, not 25
    assertThat(results.get(0).totalCloseCount()).isEqualTo(20);
  }

  /**
   * Suspicion ratio exactly at the 60% threshold.
   * 3 suspicious out of 5 = 60% = 0.60.
   * Threshold comparison is >= 0.60 → SHOULD be flagged.
   */
  @Test
  void patternReview_suspicionAtExactlyThreshold_isFlagged() {
    List<BatchEntity> batches = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      batches.add(completedBatch("diana"));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(batches);

    // 3 suspicious, 2 normal: 3/5 = 0.60 → at boundary, should flag (>=)
    when(operationsService.getBatchVariancePercent(any()))
        .thenReturn(suspiciousVariance(), suspiciousVariance(), suspiciousVariance(),
            normalVariance(), normalVariance());

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).suspiciousCloseCount()).isEqualTo(3);
  }

  /**
   * Suspicion ratio just below 60%.
   * 2 suspicious out of 5 = 40% < 0.60 → should NOT be flagged.
   */
  @Test
  void patternReview_suspicionJustBelowThreshold_notFlagged() {
    List<BatchEntity> batches = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      batches.add(completedBatch("eve"));
    }

    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(batches);

    // 2 suspicious, 3 normal: 2/5 = 0.40 < 0.60 → should NOT flag
    when(operationsService.getBatchVariancePercent(any()))
        .thenReturn(suspiciousVariance(), suspiciousVariance(),
            normalVariance(), normalVariance(), normalVariance());

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).isEmpty();
  }

  /**
   * No completed batches at all → empty result, no exception.
   */
  @Test
  void patternReview_noBatches_returnsEmpty() {
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of());

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).isEmpty();
  }

  /**
   * Batches where closedBy is null or blank should be excluded from grouping.
   * No exception expected.
   */
  @Test
  void patternReview_nullClosedBy_excluded() {
    BatchEntity b = completedBatch(null);
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(b));

    List<PatternReviewEntry> results = integrityService.getPatternReview();

    assertThat(results).isEmpty();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Cross-Validation Tests
  // ─────────────────────────────────────────────────────────────────────────────

  @BeforeEach
  void stubCrossValidationRepos() {
    // By default, return a completed batch list in cross-validation tests.
    // Individual tests override as needed.
  }

  /**
   * Tolerance boundary: exactly 15% discrepancy.
   * Logic uses strict > (greater than), so EXACTLY 15% must NOT be flagged.
   *
   * sensor=100, manual=115 → diff=15, max=115, relative=15/115*100=13.04% → NOT flagged
   * sensor=100, manual=117.647... would give exactly 15% → use sensor=100, manual=115:
   *
   * Actually: diff/max * 100 = tolerance check.
   * For exactly 15%: diff = 15, max = 100 → sensor=100, manual=85 → diff=15, max=100, 15% → NOT flagged (strict >)
   */
  @Test
  void crossValidation_exactlyAt15Percent_notFlagged() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    // sensor=100, manual=85 → diff=15, max=100, relative=15% → strictly NOT > 15, so not flagged
    InventoryLogEntity sensor = inventoryLog(batch, "PET", "sensor", "100");
    InventoryLogEntity manual = inventoryLog(batch, "PET", "manual", "85");
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(sensor, manual));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).isEmpty();
  }

  /**
   * Slightly above 15% → should be flagged.
   * sensor=100, manual=84 → diff=16, max=100, relative=16% > 15 → flagged.
   */
  @Test
  void crossValidation_justAbove15Percent_flagged() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    InventoryLogEntity sensor = inventoryLog(batch, "HDPE", "sensor", "100");
    InventoryLogEntity manual = inventoryLog(batch, "HDPE", "manual", "84");
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(sensor, manual));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).materialName()).isEqualTo("HDPE");
  }

  /**
   * Two materials in the same batch: one with discrepancy > 15%, one without.
   * Only the discrepant material should appear in results.
   */
  @Test
  void crossValidation_twoMaterials_onlyDiscrepantOneFlagged() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    // PET: sensor=100, manual=105 → 5% diff → NOT flagged
    InventoryLogEntity petSensor = inventoryLog(batch, "PET", "sensor", "100");
    InventoryLogEntity petManual = inventoryLog(batch, "PET", "manual", "105");

    // Glass: sensor=100, manual=60 → 40% diff → flagged
    InventoryLogEntity glassSensor = inventoryLog(batch, "Glass", "sensor", "100");
    InventoryLogEntity glassManual = inventoryLog(batch, "Glass", "manual", "60");

    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(petSensor, petManual, glassSensor, glassManual));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).materialName()).isEqualTo("Glass");
  }

  /**
   * Both sensor and manual quantities are zero.
   * max = 0 → the max.compareTo(ZERO) > 0 guard prevents division by zero.
   * No discrepancy should be returned.
   */
  @Test
  void crossValidation_zeroBothSides_noDivisionByZero() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    InventoryLogEntity sensor = inventoryLog(batch, "Aluminum", "sensor", "0");
    InventoryLogEntity manual = inventoryLog(batch, "Aluminum", "manual", "0");
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(sensor, manual));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).isEmpty(); // max=0, guard fires, no division
  }

  /**
   * Only sensor data present (no manual) → nothing to compare → not flagged.
   */
  @Test
  void crossValidation_onlySensor_noFlag() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    InventoryLogEntity sensor = inventoryLog(batch, "Steel", "sensor", "100");
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(sensor));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).isEmpty();
  }

  /**
   * No completed batches → returns empty immediately without hitting repository.
   */
  @Test
  void crossValidation_noCompletedBatches_returnsEmpty() {
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of());

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).isEmpty();
  }

  /**
   * OCR source counts as "manual" side → discrepancy between OCR and sensor is detected.
   */
  @Test
  void crossValidation_ocrAndSensor_discrepancyFlagged() {
    BatchEntity batch = completedBatch("operator");
    when(batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed))
        .thenReturn(List.of(batch));

    InventoryLogEntity sensor = inventoryLog(batch, "Copper", "sensor", "100");
    InventoryLogEntity ocr = inventoryLog(batch, "Copper", "ocr", "50"); // 50% diff
    when(inventoryLogRepository.findByBatch_IdInAndType(any(), any()))
        .thenReturn(List.of(sensor, ocr));

    List<CrossValidationDiscrepancy> result = integrityService.getCrossValidationDiscrepancies();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).materialName()).isEqualTo("Copper");
  }
}

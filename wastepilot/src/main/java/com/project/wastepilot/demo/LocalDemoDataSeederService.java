package com.project.wastepilot.demo;

import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.AuditChainHeadEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InsightEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.MaterialEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.CircularGrade;
import com.project.wastepilot.domain.enums.EntityType;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.MaterialCategory;
import com.project.wastepilot.domain.enums.RecoveryStatus;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.AuditChainHeadRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.repository.PasswordResetTokenRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.TemplateLineRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnomalyDetectionService;
import com.project.wastepilot.service.impl.AuditChainService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local dev-only service for seeding and resetting realistic demo data.
 *
 * <p>Strictly gated behind the {@code local} profile. Fails loudly if invoked in
 * {@code docker}, {@code prod}, or any non-local profile.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDemoDataSeederService {

  public static final String DEMO_OPERATOR_EMAIL = "operator@wastepilot.demo";
  public static final String DEMO_OPERATOR_PASSWORD = "Operator123!";
  public static final String DEMO_SUPERVISOR_EMAIL = "supervisor@wastepilot.demo";
  public static final String DEMO_SUPERVISOR_PASSWORD = "Supervisor123!";

  private final Environment environment;
  private final PasswordEncoder passwordEncoder;
  private final AuthUserRepository authUserRepository;
  private final UserSettingsRepository userSettingsRepository;
  private final MaterialRepository materialRepository;
  private final TemplateRepository templateRepository;
  private final TemplateLineRepository templateLineRepository;
  private final BatchRepository batchRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final ActivityLogRepository activityLogRepository;
  private final AuditTrailRepository auditTrailRepository;
  private final AuditChainHeadRepository auditChainHeadRepository;
  private final AnomalyRepository anomalyRepository;
  private final InsightRepository insightRepository;
  private final RedFlagRepository redFlagRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final AuditChainService auditChainService;
  private final AnomalyDetectionService anomalyDetectionService;

  private final jakarta.persistence.EntityManager entityManager;

  /**
   * Asserts that only the 'local' profile is active, and no production or docker profiles are present.
   */
  public void assertLocalProfileOnly() {
    String[] activeProfiles = environment.getActiveProfiles();
    boolean hasLocal = Arrays.stream(activeProfiles).anyMatch(p -> p.equalsIgnoreCase("local"));
    boolean hasForbidden = Arrays.stream(activeProfiles).anyMatch(p ->
        p.equalsIgnoreCase("docker") || p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

    if (!hasLocal || hasForbidden) {
      throw new SecurityException(String.format(
          "[SAFETY ERROR] Demo data seeding is strictly restricted to the 'local' development profile. " +
          "Active profile(s): %s. Refusing to run in docker/prod environments.",
          Arrays.toString(activeProfiles)));
    }
  }

  /**
   * Complete clean reset: deletes all application data and resets the audit chain head.
   */
  @Transactional
  public void resetData() {
    assertLocalProfileOnly();
    log.info("[DEMO-SEED] Resetting database to a clean empty state...");

    redFlagRepository.deleteAllInBatch();
    anomalyRepository.deleteAllInBatch();
    insightRepository.deleteAllInBatch();
    activityLogRepository.deleteAllInBatch();
    auditTrailRepository.deleteAllInBatch();
    wasteLogRepository.deleteAllInBatch();
    inventoryLogRepository.deleteAllInBatch();
    batchRepository.deleteAllInBatch();
    templateLineRepository.deleteAllInBatch();
    templateRepository.deleteAllInBatch();
    materialRepository.deleteAllInBatch();
    passwordResetTokenRepository.deleteAllInBatch();
    userSettingsRepository.deleteAllInBatch();
    authUserRepository.deleteAllInBatch();

    // Clear persistence context so no stale entities remain in session
    entityManager.flush();
    entityManager.clear();

    // Reset audit chain head to genesis
    AuditChainHeadEntity head = auditChainHeadRepository.findById(1L).orElseGet(() -> {
      AuditChainHeadEntity newHead = new AuditChainHeadEntity();
      newHead.setId(1L);
      return newHead;
    });
    head.setLastSequence(0L);
    head.setLastHash(AuditChainService.GENESIS_HASH);
    auditChainHeadRepository.saveAndFlush(head);

    log.info("[DEMO-SEED] Database reset complete.");
  }

  /**
   * Wipes existing records and seeds realistic, historically distributed textile manufacturing demo data.
   */
  @Transactional
  public LocalDemoDataResponse seedData() {
    assertLocalProfileOnly();
    log.info("[DEMO-SEED] Starting realistic demo-data seeding...");

    resetData();

    Instant now = Instant.now();

    // ── 1. Create Demo Users ───────────────────────────────────────────────────
    AuthUserEntity operator = new AuthUserEntity();
    operator.setFullName("Elena Rostova");
    operator.setEmail(DEMO_OPERATOR_EMAIL);
    operator.setPasswordHash(passwordEncoder.encode(DEMO_OPERATOR_PASSWORD));
    operator.setRole(UserRole.OPERATOR);
    operator = authUserRepository.save(operator);

    UserSettingsEntity operatorSettings = new UserSettingsEntity();
    operatorSettings.setUserId(operator.getId().toString());
    operatorSettings.setCompany("EcoThread Mills");
    operatorSettings.setEmail(DEMO_OPERATOR_EMAIL);
    operatorSettings.setTimezone("UTC");
    operatorSettings.setDailyTokenBudget(500);
    operatorSettings.setNotifyAnomalies(true);
    operatorSettings.setNotifyRecommendations(true);
    operatorSettings.setNotifyOverdueBatches(false);
    userSettingsRepository.save(operatorSettings);

    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Marcus Vance");
    supervisor.setEmail(DEMO_SUPERVISOR_EMAIL);
    supervisor.setPasswordHash(passwordEncoder.encode(DEMO_SUPERVISOR_PASSWORD));
    supervisor.setRole(UserRole.SUPERVISOR);
    supervisor = authUserRepository.save(supervisor);

    UserSettingsEntity supervisorSettings = new UserSettingsEntity();
    supervisorSettings.setUserId(supervisor.getId().toString());
    supervisorSettings.setCompany("EcoThread Mills");
    supervisorSettings.setEmail(DEMO_SUPERVISOR_EMAIL);
    supervisorSettings.setTimezone("UTC");
    supervisorSettings.setDailyTokenBudget(1000);
    supervisorSettings.setNotifyAnomalies(true);
    supervisorSettings.setNotifyRecommendations(true);
    supervisorSettings.setNotifyOverdueBatches(true);
    userSettingsRepository.save(supervisorSettings);

    // Also seed E2E supervisor account for local Playwright testing
    AuthUserEntity e2eSupervisor = new AuthUserEntity();
    e2eSupervisor.setFullName("E2E Supervisor");
    e2eSupervisor.setEmail("e2e-supervisor@wastepilot.local");
    e2eSupervisor.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
    e2eSupervisor.setRole(UserRole.SUPERVISOR);
    authUserRepository.save(e2eSupervisor);

    // ── 2. Create Materials (Textile Context) ───────────────────────────────────
    MaterialEntity matCotton = createMaterial("Organic Cotton Twill Roll", MaterialCategory.Organic,
        CircularGrade.A, "kg", new BigDecimal("2450.000"), "EcoWeave Textiles Ltd.");
    MaterialEntity matPoly = createMaterial("Recycled Polyester Fabric", MaterialCategory.Recyclable,
        CircularGrade.B, "kg", new BigDecimal("1800.000"), "Repreve Synthetics");
    MaterialEntity matIndigo = createMaterial("Natural Indigo Dye", MaterialCategory.Hazardous,
        CircularGrade.B, "kg", new BigDecimal("140.000"), "BioColor Chem Works");
    MaterialEntity matElastic = createMaterial("Elastic Ribbing Band", MaterialCategory.Recyclable,
        CircularGrade.C, "kg", new BigDecimal("320.000"), "FlexiTrim Supplies");
    MaterialEntity matCartons = createMaterial("Corrugated Garment Cartons", MaterialCategory.Packaging,
        CircularGrade.A, "kg", new BigDecimal("450.000"), "EcoPack Solutions");

    // ── 3. Create Production Templates ──────────────────────────────────────────
    // Template 1: Workwear Jacket (153 kg planned input)
    TemplateEntity tmplJacket = createTemplate("Organic Denim Workwear Jacket", "SKU-TX-JKT-01",
        new BigDecimal("18.500"), List.of(
            Map.entry(matCotton, new BigDecimal("120.000")),
            Map.entry(matPoly, new BigDecimal("25.000")),
            Map.entry(matElastic, new BigDecimal("8.000"))
        ));

    // Template 2: Poly-Cotton Hoodie (126 kg planned input)
    TemplateEntity tmplHoodie = createTemplate("Recycled Poly-Cotton Hoodie", "SKU-TX-HD-02",
        new BigDecimal("14.000"), List.of(
            Map.entry(matCotton, new BigDecimal("70.000")),
            Map.entry(matPoly, new BigDecimal("50.000")),
            Map.entry(matElastic, new BigDecimal("6.000"))
        ));

    // Template 3: Utility Tote (50 kg planned input)
    TemplateEntity tmplTote = createTemplate("Standard Cotton Utility Tote", "SKU-TX-TT-03",
        new BigDecimal("6.000"), List.of(
            Map.entry(matCotton, new BigDecimal("50.000"))
        ));

    // ── 4. Seed 30-Day Historical Completed Batches & Daily Activity ───────────
    // Batch 1 (Day -28): Tote - Early baseline run
    seedCompletedBatch(
        tmplTote.getName(), new BigDecimal("50.000"), new BigDecimal("40.000"),
        daysAgo(now, 28, 8), daysAgo(now, 28, 12),
        List.of(new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("50.000"), "Manual")),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("40.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("2.000"), WasteDestination.reuse, "Edge selvedge mulch"),
            new WstLog(matCotton.getName(), new BigDecimal("8.000"), WasteDestination.dispose, "Contaminated floor sweeps")
        ),
        "Normal initial shift run."
    );

    // Baseline waste logs for 30-day statistical anomaly window for Recycled Polyester
    // Standard normal waste for Poly is tightly clustered around ~3.5 kg (mean ~3.46 kg, stddev ~0.11 kg)
    seedStandaloneBaselineWasteLogs(tmplHoodie, matPoly.getName(), now);

    // Batch 2 (Day -24): Hoodie
    seedCompletedBatch(
        tmplHoodie.getName(), new BigDecimal("126.000"), new BigDecimal("100.000"),
        daysAgo(now, 24, 7), daysAgo(now, 24, 13),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("70.000"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("50.000"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("6.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("60.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("5.000"), WasteDestination.reuse, "Repurposed to tote bags"),
            new WstLog(matCotton.getName(), new BigDecimal("5.000"), WasteDestination.dispose, "Scrap cutting dust"),
            new WstLog(matPoly.getName(), new BigDecimal("3.500"), WasteDestination.repair, "Seam defect trim"),
            new WstLog(matPoly.getName(), new BigDecimal("4.500"), WasteDestination.dispose, "End-of-roll non-uniform trimmings")
        ),
        "Standard production batch."
    );

    // Batch 3 (Day -20): Jacket
    seedCompletedBatch(
        tmplJacket.getName(), new BigDecimal("153.000"), new BigDecimal("120.000"),
        daysAgo(now, 20, 8), daysAgo(now, 20, 15),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("120.000"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("25.000"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("8.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("100.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("9.000"), WasteDestination.reuse, "Sorted cotton scraps"),
            new WstLog(matCotton.getName(), new BigDecimal("5.000"), WasteDestination.dispose, "Soiled fabric"),
            new WstLog(matPoly.getName(), new BigDecimal("2.500"), WasteDestination.reuse, "Recycled insulation fill"),
            new WstLog(matPoly.getName(), new BigDecimal("1.500"), WasteDestination.dispose, "Unrecyclable synthetic bits")
        ),
        "Completed within standard parameters."
    );

    // Batch 4 (Day -16): Hoodie
    seedCompletedBatch(
        tmplHoodie.getName(), new BigDecimal("126.000"), new BigDecimal("102.000"),
        daysAgo(now, 16, 7), daysAgo(now, 16, 13),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("71.000"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("51.000"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("6.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("62.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("7.000"), WasteDestination.reuse, "Baled cotton offcuts"),
            new WstLog(matCotton.getName(), new BigDecimal("2.500"), WasteDestination.dispose, "Dust collectors"),
            new WstLog(matPoly.getName(), new BigDecimal("3.400"), WasteDestination.reuse, "Fiber recycling bin"),
            new WstLog(matPoly.getName(), new BigDecimal("1.500"), WasteDestination.dispose, "Burn-damaged scrap")
        ),
        "Batch finalized cleanly."
    );

    // Batch 5 (Day -13): Tote
    seedCompletedBatch(
        tmplTote.getName(), new BigDecimal("50.000"), new BigDecimal("41.000"),
        daysAgo(now, 13, 9), daysAgo(now, 13, 13),
        List.of(new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("51.000"), "Manual")),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("41.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("4.500"), WasteDestination.reuse, "Shredded for stuffing"),
            new WstLog(matCotton.getName(), new BigDecimal("1.500"), WasteDestination.dispose, "Floor sweeping")
        ),
        "Shift 2 standard completion."
    );

    // ── Batches 6 to 10: The 5 Recent Batches shown on Analytics Circularity Trend ─
    // Notice how recovery increases and dispose decreases, creating an inspiring upward trend!

    // Batch 6 (Day -10) -> Analytics Point 1 (Circularity ~82.6%)
    seedCompletedBatch(
        tmplJacket.getName(), new BigDecimal("153.000"), new BigDecimal("122.000"),
        daysAgo(now, 10, 8), daysAgo(now, 10, 15),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("122.000"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("25.000"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("8.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("105.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("9.500"), WasteDestination.reuse, "Clean denim cuttings"),
            new WstLog(matCotton.getName(), new BigDecimal("2.500"), WasteDestination.dispose, "General sweepings"),
            new WstLog(matPoly.getName(), new BigDecimal("2.500"), WasteDestination.reuse, "Thread spool recovery"),
            new WstLog(matPoly.getName(), new BigDecimal("0.500"), WasteDestination.dispose, "Tape waste")
        ),
        "Workwear production completed."
    );

    // Batch 7 (Day -7) -> Analytics Point 2 (Circularity ~85.1%)
    seedCompletedBatch(
        tmplHoodie.getName(), new BigDecimal("126.000"), new BigDecimal("101.000"),
        daysAgo(now, 7, 7), daysAgo(now, 7, 13),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("70.500"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("50.500"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("6.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("64.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("6.800"), WasteDestination.reuse, "Repurposed in tote production"),
            new WstLog(matCotton.getName(), new BigDecimal("1.200"), WasteDestination.dispose, "Cutting dust"),
            new WstLog(matPoly.getName(), new BigDecimal("3.200"), WasteDestination.repair, "Re-hemmed sleeve cutoffs"),
            new WstLog(matPoly.getName(), new BigDecimal("0.800"), WasteDestination.dispose, "Contaminated fabric")
        ),
        "Hoodie run closed with enhanced reuse."
    );

    // Daily activity filler for Day -6 to ensure complete 7-day trend
    seedShiftFillerActivity(now, 6, matCotton.getName(), new BigDecimal("145.000"), new BigDecimal("16.000"), new BigDecimal("12.000"));

    // Batch 8 (Day -5) -> Analytics Point 3 (Circularity ~88.5%)
    // DEMO MOMENT: Cross-Validation Discrepancy between Manual (50.5 kg) and Sensor (42.0 kg)
    BatchEntity batch8 = seedCompletedBatch(
        tmplTote.getName(), new BigDecimal("50.000"), new BigDecimal("42.000"),
        daysAgo(now, 5, 8), daysAgo(now, 5, 12),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("50.500"), "Manual"),
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("42.000"), "Sensor-Scale-01") // Sensor discrepancy!
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("42.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("4.400"), WasteDestination.reuse, "Recovered tote strap scrap"),
            new WstLog(matCotton.getName(), new BigDecimal("0.600"), WasteDestination.dispose, "Packaging waste")
        ),
        "Regular tote run."
    );

    // Daily activity filler for Day -4 to ensure complete 7-day trend
    seedShiftFillerActivity(now, 4, matCotton.getName(), new BigDecimal("135.000"), new BigDecimal("14.000"), new BigDecimal("10.500"));

    // Batch 9 (Day -3) -> Analytics Point 4 (Circularity ~92.7%)
    // DEMO MOMENTS:
    // 1) Variance > 5% (+9.15%): requires and has a written close reason in the audit trail!
    // 2) Anomaly Trigger: Recycled Polyester waste spike (12.5 kg vs 3.5 kg mean) triggers real statistical anomaly!
    BatchEntity batch9 = seedBatchWithHighVarianceAndAnomaly(
        tmplJacket, matCotton, matPoly, matElastic, now, 3
    );

    // Daily activity filler for Day -2 to ensure complete 7-day trend
    seedShiftFillerActivity(now, 2, matCotton.getName(), new BigDecimal("150.000"), new BigDecimal("15.500"), new BigDecimal("13.000"));

    // Batch 10 (Day -1) -> Analytics Point 5 (Circularity ~94.9%)
    seedCompletedBatch(
        tmplHoodie.getName(), new BigDecimal("126.000"), new BigDecimal("103.000"),
        daysAgo(now, 1, 8), daysAgo(now, 1, 14),
        List.of(
            new InvLog(matCotton.getName(), InventoryType.IN, new BigDecimal("70.000"), "Manual"),
            new InvLog(matPoly.getName(), InventoryType.IN, new BigDecimal("50.000"), "Manual"),
            new InvLog(matElastic.getName(), InventoryType.IN, new BigDecimal("6.000"), "Manual")
        ),
        List.of(new InvLog(matCotton.getName(), InventoryType.OUT, new BigDecimal("65.000"), "Cutting")),
        List.of(
            new WstLog(matCotton.getName(), new BigDecimal("6.000"), WasteDestination.reuse, "High grade sorted fabric waste"),
            new WstLog(matCotton.getName(), new BigDecimal("0.200"), WasteDestination.dispose, "Floor dust"),
            new WstLog(matPoly.getName(), new BigDecimal("2.600"), WasteDestination.reuse, "Repelletizing line feed"),
            new WstLog(matPoly.getName(), new BigDecimal("0.200"), WasteDestination.dispose, "Contaminated edge piece")
        ),
        "Exceptional circular recovery achieved."
    );

    // ── 5. Leave ONE Batch Genuinely RUNNING for Live Demo ──────────────────────
    // Batch 11: Started 25 minutes ago, planned 153 kg, logged 155 kg (+1.31% green variance!)
    BatchEntity runningBatch = new BatchEntity();
    runningBatch.setTemplateName(tmplJacket.getName());
    runningBatch.setPlannedInputKg(new BigDecimal("153.000"));
    runningBatch.setStartedAt(now.minus(25, ChronoUnit.MINUTES));
    runningBatch.setOutputUnits(new BigDecimal("120.000"));
    runningBatch.setWasteKg(new BigDecimal("4.500"));
    runningBatch.setStatus(BatchStatus.running);
    runningBatch = batchRepository.save(runningBatch);

    // Inventory IN logs for running batch (contributes to Day 0 trend!)
    createInventoryLog(runningBatch, matCotton.getName(), InventoryType.IN, new BigDecimal("120.000"), "kg", "Manual", now.minus(20, ChronoUnit.MINUTES));
    createInventoryLog(runningBatch, matPoly.getName(), InventoryType.IN, new BigDecimal("25.000"), "kg", "Manual", now.minus(15, ChronoUnit.MINUTES));
    createInventoryLog(runningBatch, matElastic.getName(), InventoryType.IN, new BigDecimal("10.000"), "kg", "Manual", now.minus(10, ChronoUnit.MINUTES));

    // Initial small waste log (contributes to Day 0 waste trend, batch remains running)
    WasteLogEntity runningWaste = new WasteLogEntity();
    runningWaste.setBatch(runningBatch);
    runningWaste.setMaterialName(matCotton.getName());
    runningWaste.setQuantityKg(new BigDecimal("4.500"));
    runningWaste.setDestination(WasteDestination.reuse);
    runningWaste.setRecoveryStatus(RecoveryStatus.pending);
    runningWaste.setReason("Initial pocket corner offcuts");
    runningWaste.setAiSuggestedAction("Eligible for textile shredding and secondary tote production.");
    runningWaste.setTimestamp(now.minus(5, ChronoUnit.MINUTES));
    wasteLogRepository.save(runningWaste);

    logActivity(EntityType.batch, runningBatch.getId().toString(), "batch_started",
        DEMO_OPERATOR_EMAIL, now.minus(25, ChronoUnit.MINUTES), "Started batch using " + runningBatch.getTemplateName());

    // ── 6. Seed Realistic Insights for Dashboard Preview ───────────────────────
    seedDemoInsights(now);

    log.info("[DEMO-SEED] Demo data seeding finished successfully! Running batch ID: {}", runningBatch.getId());

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("materialsCount", materialRepository.count());
    summary.put("templatesCount", templateRepository.count());
    summary.put("completedBatchesCount", batchRepository.countByStatus(BatchStatus.completed));
    summary.put("runningBatchesCount", batchRepository.countByStatus(BatchStatus.running));
    summary.put("anomaliesCount", anomalyRepository.count());
    summary.put("insightsCount", insightRepository.count());
    summary.put("auditEntriesCount", auditTrailRepository.count());

    return new LocalDemoDataResponse(
        "success",
        "Local demo data seeded successfully with realistic historical spread and active running batch.",
        Map.of("email", DEMO_OPERATOR_EMAIL, "password", DEMO_OPERATOR_PASSWORD, "role", "OPERATOR"),
        Map.of("email", DEMO_SUPERVISOR_EMAIL, "password", DEMO_SUPERVISOR_PASSWORD, "role", "SUPERVISOR"),
        runningBatch.getId().toString(),
        summary
    );
  }

  // ── Helper Methods ─────────────────────────────────────────────────────────

  private MaterialEntity createMaterial(String name, MaterialCategory category, CircularGrade grade,
      String unit, BigDecimal stock, String supplier) {
    MaterialEntity m = new MaterialEntity();
    m.setName(name);
    m.setCategory(category);
    m.setCircularGrade(grade);
    m.setUnit(unit);
    m.setStock(stock);
    m.setSupplier(supplier);
    return materialRepository.save(m);
  }

  private TemplateEntity createTemplate(String name, String sku, BigDecimal expectedWasteKg,
      List<Map.Entry<MaterialEntity, BigDecimal>> lines) {
    TemplateEntity t = new TemplateEntity();
    t.setName(name);
    t.setSku(sku);
    t.setExpectedWasteKg(expectedWasteKg);
    TemplateEntity savedT = templateRepository.save(t);

    for (var line : lines) {
      TemplateLineEntity l = new TemplateLineEntity();
      l.setTemplate(savedT);
      l.setMaterial(line.getKey());
      l.setQuantity(line.getValue());
      l.setUnit(line.getKey().getUnit());
      templateLineRepository.save(l);
      savedT.getLines().add(l);
    }
    return savedT;
  }

  private record InvLog(String material, InventoryType type, BigDecimal qty, String source) {}
  private record WstLog(String material, BigDecimal qty, WasteDestination dest, String reason) {}

  private BatchEntity seedCompletedBatch(
      String templateName, BigDecimal plannedInputKg, BigDecimal outputUnits,
      Instant startedAt, Instant closedAt,
      List<InvLog> inLogs, List<InvLog> outLogs, List<WstLog> wasteLogs,
      String closeReason
  ) {
    BigDecimal totalWaste = wasteLogs.stream().map(WstLog::qty).reduce(BigDecimal.ZERO, BigDecimal::add);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName(templateName);
    batch.setPlannedInputKg(plannedInputKg);
    batch.setStartedAt(startedAt);
    batch.setOutputUnits(outputUnits);
    batch.setWasteKg(totalWaste);
    batch.setStatus(BatchStatus.completed);
    batch.setClosedAt(closedAt);
    batch.setClosedBy(DEMO_SUPERVISOR_EMAIL);
    batch.setCloseReason(closeReason);
    batch = batchRepository.save(batch);

    for (InvLog inv : inLogs) {
      createInventoryLog(batch, inv.material(), inv.type(), inv.qty(), "kg", inv.source(), startedAt.plus(15, ChronoUnit.MINUTES));
    }
    for (InvLog inv : outLogs) {
      createInventoryLog(batch, inv.material(), inv.type(), inv.qty(), "kg", inv.source(), startedAt.plus(60, ChronoUnit.MINUTES));
    }
    for (WstLog wst : wasteLogs) {
      WasteLogEntity w = new WasteLogEntity();
      w.setBatch(batch);
      w.setMaterialName(wst.material());
      w.setQuantityKg(wst.qty());
      w.setDestination(wst.dest());
      w.setRecoveryStatus(wst.dest() == WasteDestination.dispose ? RecoveryStatus.not_applicable : RecoveryStatus.converted);
      w.setReason(wst.reason());
      w.setRecoveredAt(wst.dest() == WasteDestination.dispose ? null : closedAt);
      w.setTimestamp(closedAt.minus(30, ChronoUnit.MINUTES));
      wasteLogRepository.save(w);
    }

    logActivity(EntityType.batch, batch.getId().toString(), "batch_started",
        DEMO_OPERATOR_EMAIL, startedAt, "Started batch using " + batch.getTemplateName());
    logActivity(EntityType.batch, batch.getId().toString(), "batch_closed",
        DEMO_SUPERVISOR_EMAIL, closedAt, batch.getCloseReason());

    // Record audit trail entry for closure
    logAudit(EntityType.batch, batch.getId().toString(), "status", "running", "completed",
        DEMO_SUPERVISOR_EMAIL, closedAt, "Standard batch closure by shift supervisor.");

    return batch;
  }

  private void seedStandaloneBaselineWasteLogs(TemplateEntity tmpl, String materialName, Instant now) {
    // Generate historical baseline waste logs over the 30-day window
    // tightly clustered around 3.5 kg
    double[] historicalKgs = {3.4, 3.6, 3.3, 3.5, 3.4, 3.6, 3.5, 3.4};
    int[] daysBack = {29, 26, 23, 21, 18, 15, 12, 8};

    for (int i = 0; i < historicalKgs.length; i++) {
      Instant logTime = now.minus(daysBack[i], ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);
      // Create a dummy completed batch shell for history
      BatchEntity histBatch = new BatchEntity();
      histBatch.setTemplateName(tmpl.getName());
      histBatch.setPlannedInputKg(new BigDecimal("126.000"));
      histBatch.setStartedAt(logTime.minus(4, ChronoUnit.HOURS));
      histBatch.setOutputUnits(new BigDecimal("100.000"));
      histBatch.setWasteKg(BigDecimal.valueOf(historicalKgs[i]));
      histBatch.setStatus(BatchStatus.completed);
      histBatch.setClosedAt(logTime);
      histBatch.setClosedBy(DEMO_SUPERVISOR_EMAIL);
      histBatch.setCloseReason("Historical baseline shift.");
      histBatch = batchRepository.save(histBatch);

      WasteLogEntity w = new WasteLogEntity();
      w.setBatch(histBatch);
      w.setMaterialName(materialName);
      w.setQuantityKg(BigDecimal.valueOf(historicalKgs[i]));
      w.setDestination(WasteDestination.reuse);
      w.setRecoveryStatus(RecoveryStatus.converted);
      w.setReason("Baseline standard cutting scrap");
      w.setTimestamp(logTime);
      wasteLogRepository.save(w);
    }
  }

  private BatchEntity seedBatchWithHighVarianceAndAnomaly(
      TemplateEntity tmplJacket, MaterialEntity matCotton, MaterialEntity matPoly, MaterialEntity matElastic,
      Instant now, int daysAgo
  ) {
    Instant startedAt = daysAgo(now, daysAgo, 8);
    Instant closedAt = daysAgo(now, daysAgo, 15);

    // Planned input = 153.0 kg
    // Actual input = 132 + 27 + 8 = 167.0 kg (+9.15% variance > 5% gate!)
    BatchEntity batch = new BatchEntity();
    batch.setTemplateName(tmplJacket.getName());
    batch.setPlannedInputKg(new BigDecimal("153.000"));
    batch.setStartedAt(startedAt);
    batch.setOutputUnits(new BigDecimal("115.000"));
    batch.setWasteKg(new BigDecimal("26.500"));
    batch.setStatus(BatchStatus.completed);
    batch.setClosedAt(closedAt);
    batch.setClosedBy(DEMO_SUPERVISOR_EMAIL);
    batch.setCloseReason("Excess cutting scrap due to fabric roll edge fraying from supplier batch #4092; verified with shift supervisor.");
    batch = batchRepository.save(batch);

    createInventoryLog(batch, matCotton.getName(), InventoryType.IN, new BigDecimal("132.000"), "kg", "Manual", startedAt.plus(15, ChronoUnit.MINUTES));
    createInventoryLog(batch, matPoly.getName(), InventoryType.IN, new BigDecimal("27.000"), "kg", "Manual", startedAt.plus(20, ChronoUnit.MINUTES));
    createInventoryLog(batch, matElastic.getName(), InventoryType.IN, new BigDecimal("8.000"), "kg", "Manual", startedAt.plus(25, ChronoUnit.MINUTES));

    createInventoryLog(batch, matCotton.getName(), InventoryType.OUT, new BigDecimal("98.000"), "kg", "Cutting", startedAt.plus(60, ChronoUnit.MINUTES));

    // Normal waste for cotton
    WasteLogEntity wstCotton = new WasteLogEntity();
    wstCotton.setBatch(batch);
    wstCotton.setMaterialName(matCotton.getName());
    wstCotton.setQuantityKg(new BigDecimal("14.000"));
    wstCotton.setDestination(WasteDestination.reuse);
    wstCotton.setRecoveryStatus(RecoveryStatus.converted);
    wstCotton.setReason("Trimmed edges repurposed for insulation batting");
    wstCotton.setTimestamp(closedAt.minus(60, ChronoUnit.MINUTES));
    wasteLogRepository.save(wstCotton);

    // SPIKE WASTE LOG FOR POLYESTER: 12.5 kg vs 3.5 kg baseline mean!
    WasteLogEntity wstPolySpike = new WasteLogEntity();
    wstPolySpike.setBatch(batch);
    wstPolySpike.setMaterialName(matPoly.getName());
    wstPolySpike.setQuantityKg(new BigDecimal("12.500"));
    wstPolySpike.setDestination(WasteDestination.dispose);
    wstPolySpike.setRecoveryStatus(RecoveryStatus.not_applicable);
    wstPolySpike.setReason("Severe fraying along roll edges discarded to disposal");
    wstPolySpike.setTimestamp(closedAt.minus(45, ChronoUnit.MINUTES));
    wstPolySpike = wasteLogRepository.save(wstPolySpike);

    // Evaluate anomaly using the actual service logic
    anomalyDetectionService.evaluateWasteLog(wstPolySpike);

    logActivity(EntityType.batch, batch.getId().toString(), "batch_started",
        DEMO_OPERATOR_EMAIL, startedAt, "Started batch using " + batch.getTemplateName());
    logActivity(EntityType.batch, batch.getId().toString(), "batch_closed",
        DEMO_SUPERVISOR_EMAIL, closedAt, batch.getCloseReason());

    // Audit trail entries for high variance and close reason
    logAudit(EntityType.batch, batch.getId().toString(), "outputUnits", "120", "115",
        DEMO_SUPERVISOR_EMAIL, closedAt, "Output count adjusted due to frayed scrap material reduction.");
    logAudit(EntityType.batch, batch.getId().toString(), "closeReason", "None", batch.getCloseReason(),
        DEMO_SUPERVISOR_EMAIL, closedAt, "Variance of 9.15% exceeded the 5% threshold; mandatory close reason recorded.");

    return batch;
  }

  private void seedShiftFillerActivity(Instant now, int daysAgo, String materialName,
      BigDecimal inputKg, BigDecimal wasteKg, BigDecimal reuseKg) {
    Instant t = daysAgo(now, daysAgo, 10);
    // Loose shift inventory log
    InventoryLogEntity inv = new InventoryLogEntity();
    inv.setMaterialName(materialName);
    inv.setType(InventoryType.IN);
    inv.setQuantity(inputKg);
    inv.setUnit("kg");
    inv.setSource("Manual");
    inv.setTimestamp(t);
    inventoryLogRepository.save(inv);

    // Loose shift waste log (using latest batch or placeholder batch)
    BatchEntity refBatch = batchRepository.findAll().stream().findFirst().orElse(null);
    if (refBatch != null) {
      WasteLogEntity wstReuse = new WasteLogEntity();
      wstReuse.setBatch(refBatch);
      wstReuse.setMaterialName(materialName);
      wstReuse.setQuantityKg(reuseKg);
      wstReuse.setDestination(WasteDestination.reuse);
      wstReuse.setRecoveryStatus(RecoveryStatus.converted);
      wstReuse.setReason("Shift recovery quota");
      wstReuse.setTimestamp(t.plus(2, ChronoUnit.HOURS));
      wasteLogRepository.save(wstReuse);

      BigDecimal disposeKg = wasteKg.subtract(reuseKg).max(BigDecimal.ONE);
      WasteLogEntity wstDispose = new WasteLogEntity();
      wstDispose.setBatch(refBatch);
      wstDispose.setMaterialName(materialName);
      wstDispose.setQuantityKg(disposeKg);
      wstDispose.setDestination(WasteDestination.dispose);
      wstDispose.setRecoveryStatus(RecoveryStatus.not_applicable);
      wstDispose.setReason("General shift sweeping");
      wstDispose.setTimestamp(t.plus(3, ChronoUnit.HOURS));
      wasteLogRepository.save(wstDispose);
    }
  }

  private void seedDemoInsights(Instant now) {
    InsightEntity i1 = new InsightEntity();
    i1.setRuleId("RECOVERY_OPT_DENIM");
    i1.setTitle("High-Yield Recovery Opportunity: Denim Offcuts");
    i1.setContent("Organic denim cutting lines have maintained an 88% average recovery rate over the last 10 batches. Scraps qualify for grade-A secondary tote production with zero downcycling. (Rule-based recommendation)");
    i1.setImpactCategory("Circularity");
    i1.setStatus(InsightStatus.NEW);
    i1.setTimestamp(now.minus(4, ChronoUnit.HOURS));
    insightRepository.save(i1);

    InsightEntity i2 = new InsightEntity();
    i2.setRuleId("SUPPLIER_QUALITY_POLY");
    i2.setTitle("Supplier Material Variance Alert: Recycled Polyester");
    i2.setContent("Supplier batch #4092 exhibited edge-fraying defects leading to an isolated 12.5 kg scrap spike on Batch #9. Recommend supplier audit before releasing next PO. (Rule-based recommendation)");
    i2.setImpactCategory("Efficiency");
    i2.setStatus(InsightStatus.NEW);
    i2.setTimestamp(now.minus(12, ChronoUnit.HOURS));
    insightRepository.save(i2);

    InsightEntity i3 = new InsightEntity();
    i3.setRuleId("ENERGY_INTENSITY_HOODIE");
    i3.setTitle("Landfill Avoidance Milestone: Poly-Cotton Line");
    i3.setContent("Landfill share for Recycled Poly-Cotton Hoodie production dropped from 20.0% to 6.0% across the last 5 completed shifts, setting a new factory benchmark. (Rule-based recommendation)");
    i3.setImpactCategory("Sustainability");
    i3.setStatus(InsightStatus.applied);
    i3.setTimestamp(now.minus(24, ChronoUnit.HOURS));
    insightRepository.save(i3);
  }

  private void createInventoryLog(BatchEntity batch, String materialName, InventoryType type,
      BigDecimal quantity, String unit, String source, Instant timestamp) {
    InventoryLogEntity entity = new InventoryLogEntity();
    entity.setBatch(batch);
    entity.setMaterialName(materialName);
    entity.setType(type);
    entity.setQuantity(quantity);
    entity.setUnit(unit);
    entity.setSource(source);
    entity.setTimestamp(timestamp);
    inventoryLogRepository.save(entity);
  }

  private void logActivity(EntityType entity, String entityId, String action, String actor, Instant timestamp, String detail) {
    ActivityLogEntity a = new ActivityLogEntity();
    a.setEntity(entity);
    a.setEntityId(entityId);
    a.setAction(action);
    a.setActor(actor);
    a.setTimestamp(timestamp);
    a.setDetail(detail);
    activityLogRepository.save(a);
  }

  private void logAudit(EntityType entity, String entityId, String field, String oldValue, String newValue,
      String actor, Instant timestamp, String reason) {
    AuditTrailEntity entry = new AuditTrailEntity();
    entry.setEntity(entity);
    entry.setEntityId(entityId);
    entry.setField(field);
    entry.setOldValue(oldValue);
    entry.setNewValue(newValue);
    entry.setActor(actor);
    entry.setTimestamp(timestamp);
    entry.setReason(reason);
    auditChainService.append(entry);
  }

  private Instant daysAgo(Instant now, int days, int hourOfDay) {
    return now.minus(days, ChronoUnit.DAYS)
        .truncatedTo(ChronoUnit.DAYS)
        .plus(hourOfDay, ChronoUnit.HOURS);
  }
}

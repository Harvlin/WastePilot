package com.project.wastepilot.demo;

import com.project.wastepilot.domain.dto.analytics.AnalyticsPayloadResponse;
import com.project.wastepilot.domain.dto.dashboard.DashboardPayloadResponse;
import com.project.wastepilot.domain.dto.integrity.AuditChainVerificationResponse;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.service.AnalyticsService;
import com.project.wastepilot.service.DashboardService;
import com.project.wastepilot.service.impl.AuditChainService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class LocalDemoDataSeederIntegrationTest {

  @Autowired
  private LocalDemoDataSeederService seederService;

  @Autowired
  private AuthUserRepository authUserRepository;

  @Autowired
  private MaterialRepository materialRepository;

  @Autowired
  private TemplateRepository templateRepository;

  @Autowired
  private BatchRepository batchRepository;

  @Autowired
  private AnomalyRepository anomalyRepository;

  @Autowired
  private AuditChainService auditChainService;

  @Autowired
  private DashboardService dashboardService;

  @Autowired
  private AnalyticsService analyticsService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("Seeding creates users, materials, batches, anomalies, and active running batch")
  void testSeedDataCreatesFullRealisticDataset() {
    LocalDemoDataResponse response = seederService.seedData();

    assertThat(response.status()).isEqualTo("success");
    assertThat(response.runningBatchId()).isNotBlank();

    // 1. Two demo accounts
    var operatorOpt = authUserRepository.findByEmailIgnoreCase(LocalDemoDataSeederService.DEMO_OPERATOR_EMAIL);
    assertThat(operatorOpt).isPresent();
    assertThat(operatorOpt.get().getRole()).isEqualTo(UserRole.OPERATOR);
    assertThat(passwordEncoder.matches(LocalDemoDataSeederService.DEMO_OPERATOR_PASSWORD, operatorOpt.get().getPasswordHash())).isTrue();

    var supervisorOpt = authUserRepository.findByEmailIgnoreCase(LocalDemoDataSeederService.DEMO_SUPERVISOR_EMAIL);
    assertThat(supervisorOpt).isPresent();
    assertThat(supervisorOpt.get().getRole()).isEqualTo(UserRole.SUPERVISOR);
    assertThat(passwordEncoder.matches(LocalDemoDataSeederService.DEMO_SUPERVISOR_PASSWORD, supervisorOpt.get().getPasswordHash())).isTrue();

    // 2. Materials & Templates
    assertThat(materialRepository.count()).isGreaterThanOrEqualTo(5);
    assertThat(templateRepository.count()).isGreaterThanOrEqualTo(3);

    // 3. Batches: at least 10 completed batches + exactly 1 running batch
    List<BatchEntity> completedBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed);
    assertThat(completedBatches).hasSizeGreaterThanOrEqualTo(10);

    List<BatchEntity> runningBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.running);
    assertThat(runningBatches).hasSize(1);
    assertThat(runningBatches.get(0).getId().toString()).isEqualTo(response.runningBatchId());

    // 4. Statistical anomaly triggered
    List<AnomalyEntity> anomalies = anomalyRepository.findAll();
    assertThat(anomalies).isNotEmpty();
    assertThat(anomalies.stream().anyMatch(a ->
        "STATISTICAL_BASELINE".equals(a.getDetectionMode()) && a.getZScore() != null && a.getZScore().compareTo(BigDecimal.valueOf(2.5)) > 0
    )).isTrue();

    // 5. Audit Chain verification passes with 100% validity
    AuditChainVerificationResponse auditResult = auditChainService.verify();
    assertThat(auditResult.valid()).isTrue();
    assertThat(auditResult.entriesChecked()).isGreaterThan(0);

    // 6. Dashboard calculates realistic non-empty payload
    DashboardPayloadResponse dashboard = dashboardService.getDashboardPayload();
    assertThat(dashboard.circularScore()).isGreaterThan(0.0);
    assertThat(dashboard.metrics()).isNotEmpty();
    assertThat(dashboard.wasteTrend()).hasSize(7);
    assertThat(dashboard.topAnomaly()).isNotNull();

    // 7. Analytics payload calculates realistic upward circularity trend
    AnalyticsPayloadResponse analytics = analyticsService.getAnalyticsPayload();
    assertThat(analytics.circularityTrend()).hasSize(5);

    // Circularity points should tell an improving positive story
    double firstScore = analytics.circularityTrend().get(0).score().doubleValue();
    double lastScore = analytics.circularityTrend().get(4).score().doubleValue();
    assertThat(lastScore).isGreaterThan(firstScore);
    assertThat(lastScore).isGreaterThan(90.0);
  }

  @Test
  @DisplayName("Seeding is idempotent and repeatable without duplicating or corrupting data")
  void testSeedDataIsRepeatable() {
    seederService.seedData();
    long firstBatchCount = batchRepository.count();

    // Second run should wipe and re-seed cleanly
    LocalDemoDataResponse secondResponse = seederService.seedData();
    assertThat(secondResponse.status()).isEqualTo("success");
    assertThat(batchRepository.count()).isEqualTo(firstBatchCount);

    AuditChainVerificationResponse auditResult = auditChainService.verify();
    assertThat(auditResult.valid()).isTrue();
  }

  @Test
  @DisplayName("Reset cleanly wipes all data back to empty state")
  void testResetDataWipesClean() {
    seederService.seedData();
    assertThat(batchRepository.count()).isGreaterThan(0);

    seederService.resetData();
    assertThat(batchRepository.count()).isEqualTo(0);
    assertThat(materialRepository.count()).isEqualTo(0);
    assertThat(authUserRepository.count()).isEqualTo(0);
    assertThat(anomalyRepository.count()).isEqualTo(0);

    AuditChainVerificationResponse auditResult = auditChainService.verify();
    assertThat(auditResult.valid()).isTrue();
    assertThat(auditResult.entriesChecked()).isEqualTo(0);
  }

  @Test
  @DisplayName("Safety guard rejects non-local and docker/prod profiles loudly")
  void testSafetyGuardRejectsDockerAndProd() {
    MockEnvironment dockerEnv = new MockEnvironment();
    dockerEnv.setActiveProfiles("docker");
    LocalDemoDataSeederService dockerSeeder = new LocalDemoDataSeederService(
        dockerEnv, passwordEncoder, authUserRepository, null, materialRepository,
        templateRepository, null, batchRepository, null, null, null, null, null,
        anomalyRepository, null, null, null, auditChainService, null, null
    );

    assertThatThrownBy(dockerSeeder::assertLocalProfileOnly)
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("[SAFETY ERROR]");

    MockEnvironment prodEnv = new MockEnvironment();
    prodEnv.setActiveProfiles("prod");
    LocalDemoDataSeederService prodSeeder = new LocalDemoDataSeederService(
        prodEnv, passwordEncoder, authUserRepository, null, materialRepository,
        templateRepository, null, batchRepository, null, null, null, null, null,
        anomalyRepository, null, null, null, auditChainService, null, null
    );

    assertThatThrownBy(prodSeeder::assertLocalProfileOnly)
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("[SAFETY ERROR]");
  }
}

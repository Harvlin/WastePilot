package com.project.wastepilot.integrity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import com.project.wastepilot.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class PatternReviewIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuthUserRepository authUserRepository;

  @Autowired
  private UserSettingsRepository userSettingsRepository;

  @Autowired
  private BatchRepository batchRepository;

  @MockBean
  private com.project.wastepilot.service.OperationsService operationsService;

  @Autowired
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    batchRepository.deleteAll();
    userSettingsRepository.deleteAll();
    authUserRepository.deleteAll();
  }

  @Test
  void patternReviewRequiresSupervisor() throws Exception {
    AuthUserEntity operator = new AuthUserEntity();
    operator.setFullName("Operator");
    operator.setEmail("op@wastepilot.dev");
    operator.setPasswordHash("hashedpass");
    operator = authUserRepository.save(operator);

    UserSettingsEntity settings = new UserSettingsEntity();
    settings.setUserId(operator.getId().toString());
    settings.setCompany("Test Corp");
    settings.setEmail(operator.getEmail());
    settings.setRole(UserRole.OPERATOR);
    settings.setTimezone("UTC");
    settings.setDailyTokenBudget(1000);
    settings.setNotifyAnomalies(true);
    settings.setNotifyRecommendations(true);
    settings.setNotifyOverdueBatches(true);
    userSettingsRepository.save(settings);

    String token = jwtService.generateToken(operator.getId().toString(), UserRole.OPERATOR);

    mockMvc.perform(get("/api/v1/integrity/pattern-review")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void supervisorCanAccessPatternReview() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor");
    supervisor.setEmail("sup@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    UserSettingsEntity settings = new UserSettingsEntity();
    settings.setUserId(supervisor.getId().toString());
    settings.setCompany("Test Corp");
    settings.setEmail(supervisor.getEmail());
    settings.setRole(UserRole.SUPERVISOR);
    settings.setTimezone("UTC");
    settings.setDailyTokenBudget(1000);
    settings.setNotifyAnomalies(true);
    settings.setNotifyRecommendations(true);
    settings.setNotifyOverdueBatches(true);
    userSettingsRepository.save(settings);

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    mockMvc.perform(get("/api/v1/integrity/pattern-review")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void patternReviewCapsSampleSizeToWindowLimit() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Limit Test");
    supervisor.setEmail("sup.limit@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    UserSettingsEntity settings = new UserSettingsEntity();
    settings.setUserId(supervisor.getId().toString());
    settings.setCompany("Test Corp");
    settings.setEmail(supervisor.getEmail());
    settings.setRole(UserRole.SUPERVISOR);
    settings.setTimezone("UTC");
    settings.setDailyTokenBudget(1000);
    settings.setNotifyAnomalies(true);
    settings.setNotifyRecommendations(true);
    settings.setNotifyOverdueBatches(true);
    userSettingsRepository.save(settings);

    // Create 25 completed batches closed by this supervisor
    for (int i = 0; i < 25; i++) {
      BatchEntity batch = new BatchEntity();
      batch.setTemplateName("Template A");
      batch.setStartedAt(Instant.now().minusSeconds(3600 * 48 + i * 3600));
      batch.setOutputUnits(new BigDecimal("100.000"));
      batch.setWasteKg(BigDecimal.ZERO);
      batch.setStatus(BatchStatus.completed);
      batch.setClosedBy(supervisor.getId().toString());
      batch.setClosedAt(Instant.now().minusSeconds(3600 * 24 + i * 3600));
      batchRepository.save(batch);
    }

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    Mockito.when(operationsService.getBatchVariancePercent(ArgumentMatchers.anyString()))
        .thenReturn(new BigDecimal("4.8"));

    // Act
    mockMvc.perform(get("/api/v1/integrity/pattern-review")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].totalCloseCount").value(20)); // Capped at PATTERN_REVIEW_WINDOW (20)
  }
}

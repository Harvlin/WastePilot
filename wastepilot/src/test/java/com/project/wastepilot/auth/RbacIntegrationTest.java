package com.project.wastepilot.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.domain.dto.operations.CloseBatchRequest;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import com.project.wastepilot.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AuthUserRepository authUserRepository;

  @Autowired
  private UserSettingsRepository userSettingsRepository;

  @Autowired
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    userSettingsRepository.deleteAll();
    authUserRepository.deleteAll();
  }

  @Test
  void operatorCannotCloseBatch() throws Exception {
    AuthUserEntity operator = new AuthUserEntity();
    operator.setFullName("Operator Bob");
    operator.setEmail("operator@wastepilot.dev");
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

    String operatorToken = jwtService.generateToken(operator.getId().toString(), UserRole.OPERATOR);

    CloseBatchRequest request = new CloseBatchRequest("batch-123", new BigDecimal("100"), "Normal close");
    
    mockMvc.perform(post("/api/v1/operations/batch-close")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void supervisorCanAccessCloseBatch() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Alice");
    supervisor.setEmail("supervisor@wastepilot.dev");
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

    String supervisorToken = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    CloseBatchRequest request = new CloseBatchRequest("batch-123", new BigDecimal("100"), "Normal close");
    
    // We expect 400 Bad Request or 404 Not Found because batch-123 doesn't exist, 
    // but NOT 403 Forbidden because authorization should pass.
    mockMvc.perform(post("/api/v1/operations/batch-close")
            .header("Authorization", "Bearer " + supervisorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(result -> {
            int statusCode = result.getResponse().getStatus();
            assert statusCode != 403 : "Supervisor should not get 403 Forbidden";
        });
  }
}

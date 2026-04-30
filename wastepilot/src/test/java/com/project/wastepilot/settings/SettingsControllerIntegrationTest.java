package com.project.wastepilot.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AuthUserRepository authUserRepository;

  @Autowired
  private UserSettingsRepository userSettingsRepository;

  @BeforeEach
  void setUp() {
    userSettingsRepository.deleteAll();
    authUserRepository.deleteAll();
  }

  @Test
  void shouldGetAndUpdateSettingsForAuthenticatedUser() throws Exception {
    String token = signupAndGetToken("settings-user@wastepilot.dev", "superSecret123", "Settings User");

    mockMvc.perform(get("/api/v1/settings")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("WastePilot Workspace"))
        .andExpect(jsonPath("$.email").value("settings-user@wastepilot.dev"))
        .andExpect(jsonPath("$.timezone").value("UTC"))
        .andExpect(jsonPath("$.notifyInsights").value(true));

    Map<String, Object> updatePayload = Map.of(
        "companyName", "Pilot Circular",
        "email", "ops@pilot.circular",
        "role", "Operations Lead",
        "timezone", "Asia/Jakarta",
        "dailyTokenBudget", 18000,
        "notifyAnomalies", true,
        "notifyInsights", false
    );

    mockMvc.perform(put("/api/v1/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Pilot Circular"))
        .andExpect(jsonPath("$.email").value("ops@pilot.circular"))
        .andExpect(jsonPath("$.role").value("Operations Lead"))
        .andExpect(jsonPath("$.timezone").value("Asia/Jakarta"))
        .andExpect(jsonPath("$.dailyTokenBudget").value(18000))
        .andExpect(jsonPath("$.notifyInsights").value(false));
  }

  @Test
  void shouldRejectSettingsWithoutToken() throws Exception {
    mockMvc.perform(get("/api/v1/settings"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectInvalidTimezone() throws Exception {
    String token = signupAndGetToken("tz-user@wastepilot.dev", "superSecret123", "Timezone User");
    Map<String, Object> updatePayload = Map.of(
        "companyName", "Pilot Circular",
        "email", "ops@pilot.circular",
        "role", "Operations Lead",
        "timezone", "Mars/Olympus",
        "dailyTokenBudget", 18000,
        "notifyAnomalies", true,
        "notifyInsights", false
    );

    mockMvc.perform(put("/api/v1/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
  }

  @Test
  void shouldRejectInvalidEmailAndOutOfRangeBudget() throws Exception {
    String token = signupAndGetToken("validation-user@wastepilot.dev", "superSecret123", "Validation User");
    Map<String, Object> updatePayload = Map.of(
        "companyName", "Pilot Circular",
        "email", "invalid-email",
        "role", "Operations Lead",
        "timezone", "Asia/Jakarta",
        "dailyTokenBudget", 200000,
        "notifyAnomalies", true,
        "notifyInsights", false
    );

    mockMvc.perform(put("/api/v1/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private String signupAndGetToken(String email, String password, String fullName) throws Exception {
    SignupRequest signupRequest = new SignupRequest(fullName, email, password);
    MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated())
        .andReturn();

    Map<?, ?> signupBody = objectMapper.readValue(signupResult.getResponse().getContentAsString(), Map.class);
    return (String) signupBody.get("accessToken");
  }
}

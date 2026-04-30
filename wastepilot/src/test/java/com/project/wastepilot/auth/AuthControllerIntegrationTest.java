package com.project.wastepilot.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.repository.AuthUserRepository;
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
class AuthControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AuthUserRepository authUserRepository;

  @BeforeEach
  void setUp() {
    authUserRepository.deleteAll();
  }

  @Test
  void signupLoginAndMeFlowWorks() throws Exception {
    SignupRequest signupRequest = new SignupRequest("Raka Pratama", "raka@wastepilot.dev", "superSecret123");

    MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.user.email").value("raka@wastepilot.dev"))
        .andReturn();

    Map<?, ?> signupBody = objectMapper.readValue(signupResult.getResponse().getContentAsString(), Map.class);
    String accessToken = (String) signupBody.get("accessToken");

    mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("raka@wastepilot.dev"))
        .andExpect(jsonPath("$.fullName").value("Raka Pratama"));

    LoginRequest loginRequest = new LoginRequest("raka@wastepilot.dev", "superSecret123");
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value("raka@wastepilot.dev"));
  }

  @Test
  void duplicateEmailShouldReturnConflict() throws Exception {
    SignupRequest request = new SignupRequest("Same User", "same@wastepilot.dev", "superSecret123");

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void loginWithWrongPasswordShouldReturnUnauthorized() throws Exception {
    SignupRequest signupRequest = new SignupRequest("Nadia", "nadia@wastepilot.dev", "superSecret123");
    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated());

    LoginRequest badLogin = new LoginRequest("nadia@wastepilot.dev", "wrongPassword123");
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(badLogin)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void meWithoutTokenShouldReturnUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized());
  }
}

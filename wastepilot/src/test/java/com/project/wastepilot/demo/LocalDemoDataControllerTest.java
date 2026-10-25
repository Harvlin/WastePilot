package com.project.wastepilot.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalDemoDataControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("GET /api/v1/dev/status returns active local dev status")
  void testDevStatus() throws Exception {
    mockMvc.perform(get("/api/v1/dev/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.profile").value("local"));
  }

  @Test
  @DisplayName("POST /api/v1/dev/seed seeds data and returns operator and supervisor credentials")
  void testDevSeedEndpoint() throws Exception {
    mockMvc.perform(post("/api/v1/dev/seed").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.operatorCredentials.email").value("operator@wastepilot.demo"))
        .andExpect(jsonPath("$.operatorCredentials.password").value("Operator123!"))
        .andExpect(jsonPath("$.supervisorCredentials.email").value("supervisor@wastepilot.demo"))
        .andExpect(jsonPath("$.supervisorCredentials.password").value("Supervisor123!"))
        .andExpect(jsonPath("$.runningBatchId").isNotEmpty())
        .andExpect(jsonPath("$.summary.completedBatchesCount").isNumber());
  }

  @Test
  @DisplayName("POST /api/v1/dev/reset wipes data cleanly")
  void testDevResetEndpoint() throws Exception {
    mockMvc.perform(post("/api/v1/dev/reset").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("reset_completed"));
  }
}

package com.project.wastepilot.operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.RedFlagRepository;
import com.project.wastepilot.repository.WasteLogRepository;
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
class OperationsControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private BatchRepository batchRepository;

  @Autowired
  private InventoryLogRepository inventoryLogRepository;

  @Autowired
  private WasteLogRepository wasteLogRepository;

  @Autowired
  private ActivityLogRepository activityLogRepository;

  @Autowired
  private AuditTrailRepository auditTrailRepository;

  @Autowired
  private RedFlagRepository redFlagRepository;

  @BeforeEach
  void setUp() {
    auditTrailRepository.deleteAll();
    activityLogRepository.deleteAll();
    wasteLogRepository.deleteAll();
    inventoryLogRepository.deleteAll();
    batchRepository.deleteAll();
    redFlagRepository.deleteAll();
  }

  @Test
  void operationsFlowShouldWork() throws Exception {
    String token = signupAndGetToken("operations-user@wastepilot.dev");

    MvcResult batchResult = mockMvc.perform(post("/api/v1/operations/batches")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "templateName", "Plain Tee v2",
                "outputUnits", 280,
                "wasteKg", 14
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("running"))
        .andReturn();
    String batchId = objectMapper.readTree(batchResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(post("/api/v1/operations/inventory-logs")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "batchId", batchId,
                "materialName", "Cotton Roll 280gsm",
                "type", "OUT",
                "quantity", 20,
                "unit", "kg",
                "source", "Manual"
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchId").value(batchId));

    MvcResult wasteResult = mockMvc.perform(post("/api/v1/operations/waste-logs")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "batchId", batchId,
                "materialName", "Cotton Trim",
                "quantityKg", 7,
                "destination", "reuse",
                "reason", "Operator input",
                "aiSuggestedAction", "Route this material for immediate reuse in secondary production."
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchId").value(batchId))
        .andExpect(jsonPath("$.recoveryStatus").value("pending"))
        .andReturn();
    String wasteId = objectMapper.readTree(wasteResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(post("/api/v1/operations/waste-logs/recover")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("wasteLogId", wasteId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wasteLog.recoveryStatus").value("converted"))
        .andExpect(jsonPath("$.inventoryLog.type").value("IN"));

    mockMvc.perform(get("/api/v1/operations/batch-close/summary/" + batchId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchId").value(batchId))
        .andExpect(jsonPath("$.confidenceScore").isNumber());

    mockMvc.perform(post("/api/v1/operations/batch-close")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "batchId", batchId,
                "outputUnits", 275,
                "closeReason", "Final adjustment from QA."
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchId").value(batchId));

    mockMvc.perform(get("/api/v1/operations")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batches[0].id").value(batchId))
        .andExpect(jsonPath("$.wasteLogs[0].id").value(wasteId));

    mockMvc.perform(get("/api/v1/integrity/activity-logs")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].entity").isString());

    mockMvc.perform(get("/api/v1/integrity/overview")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageConfidenceScore").isNumber());
  }

  private String signupAndGetToken(String email) throws Exception {
    SignupRequest signupRequest = new SignupRequest("Operations User", email, "superSecret123");
    MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readTree(signupResult.getResponse().getContentAsString()).get("accessToken").asText();
  }
}


package com.project.wastepilot.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.repository.MaterialRepository;
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
class MaterialsControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MaterialRepository materialRepository;

  @BeforeEach
  void setUp() {
    materialRepository.deleteAll();
  }

  @Test
  void shouldCreateUpdateAndFetchMaterials() throws Exception {
    String token = signupAndGetToken("material-user@wastepilot.dev");

    Map<String, Object> createPayload = Map.of(
        "name", "Cotton Roll 280gsm",
        "category", "Recyclable",
        "unit", "kg",
        "circularGrade", "A",
        "stock", 420,
        "supplier", "SourceTex"
    );

    MvcResult createResult = mockMvc.perform(post("/api/v1/materials")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cotton Roll 280gsm"))
        .andExpect(jsonPath("$.category").value("Recyclable"))
        .andReturn();

    String materialId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    Map<String, Object> updatePayload = Map.of(
        "name", "Cotton Roll 280gsm",
        "category", "Biodegradable",
        "unit", "kg",
        "circularGrade", "B",
        "stock", 380,
        "supplier", "EcoCore"
    );

    mockMvc.perform(put("/api/v1/materials/" + materialId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("Biodegradable"))
        .andExpect(jsonPath("$.circularGrade").value("B"))
        .andExpect(jsonPath("$.supplier").value("EcoCore"));

    mockMvc.perform(get("/api/v1/materials")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(materialId));

    mockMvc.perform(delete("/api/v1/materials/" + materialId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/materials")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private String signupAndGetToken(String email) throws Exception {
    SignupRequest signupRequest = new SignupRequest("Material User", email, "superSecret123");
    MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readTree(signupResult.getResponse().getContentAsString()).get("accessToken").asText();
  }
}

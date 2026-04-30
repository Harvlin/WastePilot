package com.project.wastepilot.template;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.domain.entity.MaterialEntity;
import com.project.wastepilot.domain.enums.CircularGrade;
import com.project.wastepilot.domain.enums.MaterialCategory;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.repository.TemplateRepository;
import java.math.BigDecimal;
import java.util.List;
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
class TemplatesControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MaterialRepository materialRepository;

  @Autowired
  private TemplateRepository templateRepository;

  @BeforeEach
  void setUp() {
    templateRepository.deleteAll();
    materialRepository.deleteAll();
  }

  @Test
  void shouldCreateUpdateAndFetchTemplates() throws Exception {
    String token = signupAndGetToken("template-user@wastepilot.dev");
    MaterialEntity material = createMaterial();

    Map<String, Object> createPayload = Map.of(
        "name", "Plain Tee v2",
        "sku", "PT-02",
        "expectedWasteKg", 12,
        "lines", List.of(Map.of(
            "materialId", material.getId().toString(),
            "materialName", material.getName(),
            "quantity", 28,
            "unit", "kg"
        ))
    );

    MvcResult createResult = mockMvc.perform(post("/api/v1/templates")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Plain Tee v2"))
        .andExpect(jsonPath("$.sku").value("PT-02"))
        .andReturn();

    String templateId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    Map<String, Object> updatePayload = Map.of(
        "name", "Plain Tee v3",
        "sku", "PT-03",
        "expectedWasteKg", 15,
        "lines", List.of(Map.of(
            "materialId", material.getId().toString(),
            "materialName", material.getName(),
            "quantity", 30,
            "unit", "kg"
        ))
    );

    mockMvc.perform(put("/api/v1/templates/" + templateId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Plain Tee v3"))
        .andExpect(jsonPath("$.sku").value("PT-03"));

    mockMvc.perform(get("/api/v1/templates")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(templateId));

    mockMvc.perform(delete("/api/v1/templates/" + templateId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/templates")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private String signupAndGetToken(String email) throws Exception {
    SignupRequest signupRequest = new SignupRequest("Template User", email, "superSecret123");
    MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readTree(signupResult.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private MaterialEntity createMaterial() {
    MaterialEntity material = new MaterialEntity();
    material.setName("Cotton Roll 280gsm");
    material.setCategory(MaterialCategory.Recyclable);
    material.setUnit("kg");
    material.setCircularGrade(CircularGrade.A);
    material.setStock(BigDecimal.valueOf(420));
    material.setSupplier("SourceTex");
    return materialRepository.save(material);
  }
}

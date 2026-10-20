package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.sensor.SensorIngestRequest;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.InsightRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class SensorControllerIntegrationTest {

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
  private AnomalyRepository anomalyRepository;
  
  @Autowired
  private InsightRepository insightRepository;
  
  @BeforeEach
  void setUp() {
    anomalyRepository.deleteAll();
    insightRepository.deleteAll();
    wasteLogRepository.deleteAll();
    inventoryLogRepository.deleteAll();
    batchRepository.deleteAll();
  }

  @Test
  void shouldIngestSensorData() throws Exception {
    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Test Template");
    batch.setPlannedInputKg(BigDecimal.ZERO);
    batch.setStartedAt(Instant.now());
    batch.setOutputUnits(BigDecimal.valueOf(100));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.running);
    batch = batchRepository.save(batch);

    SensorIngestRequest request = new SensorIngestRequest(
        batch.getId().toString(),
        "Cotton",
        BigDecimal.valueOf(50.5),
        "kg",
        "weight",
        null,
        null
    );

    mockMvc.perform(post("/api/v1/sensors/ingest")
            .header("X-Sensor-API-Key", "test-sensor-ingest-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("sensor"))
        .andExpect(jsonPath("$.type").value("IN"));
  }

  @Test
  void shouldRejectSensorIngestWithoutValidSensorKey() throws Exception {
    SensorIngestRequest request = new SensorIngestRequest(
        "not-used", "Cotton", BigDecimal.valueOf(1), "kg", "weight", null, null);

    mockMvc.perform(post("/api/v1/sensors/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SENSOR_AUTHENTICATION_FAILED"));

    mockMvc.perform(post("/api/v1/sensors/ingest")
            .header("X-Sensor-API-Key", "incorrect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SENSOR_AUTHENTICATION_FAILED"));
  }
}

package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.domain.dto.operations.CloseBatchRequest;
import com.project.wastepilot.domain.dto.operations.UpdateOutputUnitsRequest;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.repository.AuditTrailRepository;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class AuditTrailIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AuthUserRepository authUserRepository;

  @Autowired
  private BatchRepository batchRepository;

  @Autowired
  private AuditTrailRepository auditTrailRepository;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private com.project.wastepilot.repository.WasteLogRepository wasteLogRepository;

  @Autowired
  private com.project.wastepilot.repository.InventoryLogRepository inventoryLogRepository;

  @BeforeEach
  void setUp() {
    auditTrailRepository.deleteAll();
    wasteLogRepository.deleteAll();
    inventoryLogRepository.deleteAll();
    batchRepository.deleteAll();
    authUserRepository.deleteAll();
  }

  @Test
  void logAuditDoesNotAppendReasonToActor() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Alice");
    supervisor.setEmail("alice@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Template A");
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(new BigDecimal("100.000"));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.running);
    batch = batchRepository.save(batch);

    // Close the batch with a different output to trigger logAudit
    CloseBatchRequest request = new CloseBatchRequest(batch.getId().toString(), new BigDecimal("105.000"), "Final count on batch closure.");

    mockMvc.perform(post("/api/v1/operations/batch-close")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    List<AuditTrailEntity> logs = auditTrailRepository.findAll();
    assertThat(logs).hasSize(1);
    
    AuditTrailEntity log = logs.get(0);
    assertThat(log.getActor()).doesNotContain("|reason=");
    assertThat(log.getActor()).isEqualTo(supervisor.getId().toString());
    assertThat(log.getReason()).isEqualTo("Final count on batch closure.");
  }

  @Test
  void supervisorCanCorrectOutputUnitsWithValidReason() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Bob");
    supervisor.setEmail("bob@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Template A");
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(new BigDecimal("100.000"));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.completed);
    batch = batchRepository.save(batch);

    UpdateOutputUnitsRequest request = new UpdateOutputUnitsRequest(new BigDecimal("120.000"), "Typo in original log");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/operations/batches/" + batch.getId() + "/output-units")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    BatchEntity updated = batchRepository.findById(batch.getId()).get();
    assertThat(updated.getOutputUnits()).isEqualByComparingTo("120.000");

    List<AuditTrailEntity> logs = auditTrailRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getReason()).isEqualTo("Typo in original log");
  }

  @Test
  void supervisorCorrectionFailsIfReasonTooShort() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Charlie");
    supervisor.setEmail("charlie@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Template A");
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(new BigDecimal("100.000"));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.completed);
    batch = batchRepository.save(batch);

    UpdateOutputUnitsRequest request = new UpdateOutputUnitsRequest(new BigDecimal("120.000"), "typo");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/operations/batches/" + batch.getId() + "/output-units")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("CORRECTION_REASON_REQUIRED"));
  }

  @Test
  void operatorCannotCorrectOutputUnits() throws Exception {
    AuthUserEntity operator = new AuthUserEntity();
    operator.setFullName("Operator Dan");
    operator.setEmail("dan@wastepilot.dev");
    operator.setPasswordHash("hashedpass");
    operator = authUserRepository.save(operator);

    String token = jwtService.generateToken(operator.getId().toString(), UserRole.OPERATOR);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Template A");
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(new BigDecimal("100.000"));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.completed);
    batch = batchRepository.save(batch);

    UpdateOutputUnitsRequest request = new UpdateOutputUnitsRequest(new BigDecimal("120.000"), "Typo in original log");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/operations/batches/" + batch.getId() + "/output-units")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cannotCorrectOutputUnitsForRunningBatch() throws Exception {
    AuthUserEntity supervisor = new AuthUserEntity();
    supervisor.setFullName("Supervisor Eve");
    supervisor.setEmail("eve@wastepilot.dev");
    supervisor.setPasswordHash("hashedpass");
    supervisor = authUserRepository.save(supervisor);

    String token = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Template A");
    batch.setStartedAt(Instant.now().minusSeconds(3600));
    batch.setOutputUnits(new BigDecimal("100.000"));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.running);
    batch = batchRepository.save(batch);

    UpdateOutputUnitsRequest request = new UpdateOutputUnitsRequest(new BigDecimal("120.000"), "Typo in original log");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/operations/batches/" + batch.getId() + "/output-units")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("BATCH_NOT_COMPLETED"));
  }
}

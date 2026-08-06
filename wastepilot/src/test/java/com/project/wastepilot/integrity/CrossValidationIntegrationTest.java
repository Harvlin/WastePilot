package com.project.wastepilot.integrity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.UserRole;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import com.project.wastepilot.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CrossValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    @Autowired
    private JwtService jwtService;

    private String supervisorToken;

    @BeforeEach
    void setUp() {
        inventoryLogRepository.deleteAll();
        batchRepository.deleteAll();
        userSettingsRepository.deleteAll();
        authUserRepository.deleteAll();

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

        supervisorToken = jwtService.generateToken(supervisor.getId().toString(), UserRole.SUPERVISOR);
    }

    @Test
    void testCrossValidationMatchesSources_NoDiscrepancy() throws Exception {
        BatchEntity batch = createBatch();
        createLog(batch, "PET", "sensor", "100");
        createLog(batch, "PET", "manual", "105"); // 5% diff

        mockMvc.perform(get("/api/v1/integrity/cross-validation")
                .header("Authorization", "Bearer " + supervisorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testCrossValidationFlagsDiscrepancy() throws Exception {
        BatchEntity batch = createBatch();
        createLog(batch, "HDPE", "sensor", "50");
        createLog(batch, "HDPE", "manual", "100"); // 50% diff

        mockMvc.perform(get("/api/v1/integrity/cross-validation")
                .header("Authorization", "Bearer " + supervisorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].materialName").value("HDPE"));
    }

    @Test
    void testCrossValidationSingleSource_NoFlag() throws Exception {
        BatchEntity batch = createBatch();
        createLog(batch, "Glass", "sensor", "100");

        mockMvc.perform(get("/api/v1/integrity/cross-validation")
                .header("Authorization", "Bearer " + supervisorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    private BatchEntity createBatch() {
        BatchEntity batch = new BatchEntity();
        batch.setTemplateName("Test Template");
        batch.setStatus(BatchStatus.completed);
        batch.setStartedAt(Instant.now().minusSeconds(3600));
        batch.setClosedAt(Instant.now());
        batch.setClosedBy("super");
        batch.setWasteKg(BigDecimal.ZERO);
        batch.setOutputUnits(BigDecimal.TEN);
        return batchRepository.save(batch);
    }

    private void createLog(BatchEntity batch, String material, String source, String qty) {
        InventoryLogEntity log = new InventoryLogEntity();
        log.setBatch(batch);
        log.setMaterialName(material);
        log.setType(InventoryType.IN);
        log.setSource(source);
        log.setQuantity(new BigDecimal(qty));
        log.setUnit("kg");
        log.setTimestamp(Instant.now());
        inventoryLogRepository.save(log);
    }
}

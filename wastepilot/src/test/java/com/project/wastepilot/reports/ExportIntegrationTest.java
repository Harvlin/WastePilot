package com.project.wastepilot.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.hamcrest.Matchers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private JwtService jwtService;

    private String userToken;

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        authUserRepository.deleteAll();

        AuthUserEntity operator = new AuthUserEntity();
        operator.setFullName("Operator");
        operator.setEmail("op@wastepilot.dev");
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

        userToken = jwtService.generateToken(operator.getId().toString(), UserRole.OPERATOR);
    }

    @Test
    void testExportCsv() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export?format=csv&period=weekly")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv"))
            .andExpect(header().string("Content-Disposition", Matchers.containsString("form-data; name=\"attachment\"; filename=\"reports.csv\"")))
            .andExpect(content().string(Matchers.containsString("Label,Transactions")));
    }

    @Test
    void testExportPdf() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export?format=pdf&period=monthly")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", Matchers.containsString("form-data; name=\"attachment\"; filename=\"reports.pdf\"")));
    }
}

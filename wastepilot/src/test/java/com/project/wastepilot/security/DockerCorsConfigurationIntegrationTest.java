package com.project.wastepilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:docker_cors_test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "CORS_ALLOWED_ORIGIN_0=https://waste-pilot.vercel.app",
    "CORS_ALLOWED_ORIGIN_1=https://wastepilot.id",
    "CORS_ALLOWED_ORIGIN_2=https://www.wastepilot.id",
    "JWT_SECRET=production-grade-jwt-secret-for-docker-cors-test-min-256-bits",
    "AUDIT_CHAIN_HMAC_KEY=11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff",
    "SENSOR_INGEST_API_KEY=test-sensor-api-key-docker-test"
})
@ActiveProfiles({"test", "docker"})
@AutoConfigureMockMvc
class DockerCorsConfigurationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SecurityProps securityProps;

  @Autowired
  private CorsConfigurationSource corsConfigurationSource;

  @Test
  @DisplayName("Docker profile binds all three configured CORS origins from application-docker.properties")
  void dockerProfileBindsAllThreeConfiguredOrigins() {
    assertThat(securityProps.corsAllowedOrigins())
        .containsExactly(
            "https://waste-pilot.vercel.app",
            "https://wastepilot.id",
            "https://www.wastepilot.id"
        );
  }

  @Test
  @DisplayName("Docker profile allows preflight from the original Vercel origin")
  void allowsOriginalVercelOriginPreflight() throws Exception {
    mockMvc.perform(options("/api/v1/auth/login")
            .header("Origin", "https://waste-pilot.vercel.app")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://waste-pilot.vercel.app"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  @DisplayName("Docker profile allows preflight from wastepilot.id apex origin")
  void allowsWastepilotIdApexOriginPreflight() throws Exception {
    mockMvc.perform(options("/api/v1/auth/login")
            .header("Origin", "https://wastepilot.id")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://wastepilot.id"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  @DisplayName("Docker profile allows preflight from www.wastepilot.id canonical origin")
  void allowsWwwWastepilotIdCanonicalOriginPreflight() throws Exception {
    mockMvc.perform(options("/api/v1/auth/login")
            .header("Origin", "https://www.wastepilot.id")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://www.wastepilot.id"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  @DisplayName("Docker profile rejects preflight from unconfigured origin")
  void rejectsUnrelatedOriginPreflight() throws Exception {
    mockMvc.perform(options("/api/v1/auth/login")
            .header("Origin", "https://malicious-site.example")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Nested
  @SpringBootTest(properties = {
      "spring.datasource.url=jdbc:h2:mem:docker_cors_default_test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "CORS_ALLOWED_ORIGIN_0=https://waste-pilot.vercel.app",
      "JWT_SECRET=production-grade-jwt-secret-for-docker-cors-test-min-256-bits",
      "AUDIT_CHAIN_HMAC_KEY=11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff",
      "SENSOR_INGEST_API_KEY=test-sensor-api-key-docker-test"
  })
  @ActiveProfiles({"test", "docker"})
  @AutoConfigureMockMvc
  class UnsetOptionalOriginsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    @DisplayName("Docker profile with unset optional origins filters out blanks and only allows origin 0")
    void dropsBlankOriginsWhenUnset() throws Exception {
      CorsConfiguration cfg = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());
      assertThat(cfg).isNotNull();
      assertThat(cfg.getAllowedOrigins())
          .containsExactly("https://waste-pilot.vercel.app");

      mockMvc.perform(options("/api/v1/auth/login")
              .header("Origin", "https://waste-pilot.vercel.app")
              .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "https://waste-pilot.vercel.app"));

      mockMvc.perform(options("/api/v1/auth/login")
              .header("Origin", "https://www.wastepilot.id")
              .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isForbidden());
    }
  }
}

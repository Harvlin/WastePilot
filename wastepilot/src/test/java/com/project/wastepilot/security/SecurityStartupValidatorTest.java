package com.project.wastepilot.security;

import com.project.wastepilot.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

import static com.project.wastepilot.security.SecurityStartupValidator.DEV_AUDIT_HMAC_KEY;
import static com.project.wastepilot.security.SecurityStartupValidator.LOCAL_DEV_JWT_SECRET;
import static com.project.wastepilot.security.SecurityStartupValidator.MOCK_GEMINI_KEY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link SecurityStartupValidator}.
 * No Spring context — uses Mockito only to keep the suite fast.
 */
@ExtendWith(MockitoExtension.class)
class SecurityStartupValidatorTest {

  private static final String REAL_JWT_SECRET    = "super-secret-production-key-abcdef1234567890";
  private static final String REAL_GEMINI_KEY    = "AIzaSyD-real-key-example";
  private static final String REAL_AUDIT_HMAC    = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
  private static final String REAL_SENSOR_KEY    = "real-sensor-ingest-key-0001";

  private static final DefaultApplicationArguments NO_ARGS =
      new DefaultApplicationArguments(new String[]{});

  // ── Helper factories ─────────────────────────────────────────────────────────

  private static JwtProperties jwtProps(String secret) {
    return new JwtProperties(secret, 28800L, 604800L);
  }

  private static GeminiProperties geminiProps(String apiKey) {
    return new GeminiProperties(apiKey, "gemini-1.5-flash",
        "https://generativelanguage.googleapis.com/v1beta/models");
  }

  /**
   * Creates an Environment mock with the given active profiles and default "good" values
   * for the sensor API key. Callers can override stubs individually after calling this.
   */
  private static Environment envWithProfiles(String... profiles) {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(profiles);
    lenient().when(env.getProperty("wastepilot.sensor.api-key", "")).thenReturn(REAL_SENSOR_KEY);
    return env;
  }

  /** Builds a validator where all secrets are valid production values. */
  private static SecurityStartupValidator allGoodValidator(String... profiles) {
    return new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles(profiles),
        REAL_AUDIT_HMAC
    );
  }

  // ── JWT_SECRET checks ────────────────────────────────────────────────────────

  @Test
  void dockerProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker"),
        REAL_AUDIT_HMAC
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void prodProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("prod"),
        REAL_AUDIT_HMAC
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void noActiveProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles(), // no profiles active — NOT in safe set
        REAL_AUDIT_HMAC
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void dockerProfile_realJwtSecret_doesNotThrow() {
    assertThatCode(() -> allGoodValidator("docker").run(NO_ARGS)).doesNotThrowAnyException();
  }

  @Test
  void noActiveProfile_realJwtSecret_doesNotThrow() {
    assertThatCode(() -> allGoodValidator().run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── Safe-profile bypasses ────────────────────────────────────────────────────

  @Test
  void localProfile_defaultSecrets_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("local"),
        "" // blank audit key — allowed in local
    );

    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  @Test
  void testProfile_defaultSecrets_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("test"),
        DEV_AUDIT_HMAC_KEY // dev value — allowed in test
    );

    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── AUDIT_CHAIN_HMAC_KEY checks ──────────────────────────────────────────────

  @Test
  void dockerProfile_missingAuditHmacKey_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker"),
        "" // blank — must be rejected outside safe profiles
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUDIT_CHAIN_HMAC_KEY");
  }

  @Test
  void dockerProfile_devAuditHmacKey_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker"),
        DEV_AUDIT_HMAC_KEY // dev sentinel — must be rejected in docker profile
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUDIT_CHAIN_HMAC_KEY");
  }

  @Test
  void dockerProfile_placeholderAuditHmacKey_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker"),
        "REPLACE_WITH_STRONG_HMAC_KEY"
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUDIT_CHAIN_HMAC_KEY");
  }

  @Test
  void dockerProfile_realAuditHmacKey_doesNotThrow() {
    assertThatCode(() -> allGoodValidator("docker").run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── SENSOR_INGEST_API_KEY checks ─────────────────────────────────────────────

  @Test
  void dockerProfile_missingSensorApiKey_throwsIllegalState() {
    Environment environment = envWithProfiles("docker");
    when(environment.getProperty("wastepilot.sensor.api-key", "")).thenReturn("");

    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        environment,
        REAL_AUDIT_HMAC
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SENSOR_INGEST_API_KEY");
  }

  @Test
  void dockerProfile_placeholderSensorApiKey_throwsIllegalState() {
    Environment environment = envWithProfiles("docker");
    when(environment.getProperty("wastepilot.sensor.api-key", ""))
        .thenReturn("REPLACE_WITH_STRONG_SENSOR_INGEST_KEY");

    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        environment,
        REAL_AUDIT_HMAC
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SENSOR_INGEST_API_KEY");
  }

  // ── GEMINI_API_KEY — warning only, must NOT throw ────────────────────────────

  @Test
  void dockerProfile_mockGeminiKey_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("docker"),
        REAL_AUDIT_HMAC
    );

    // Warning-level log only — application still starts
    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── Collect-all failures: multiple missing secrets reported together ──────────

  @Test
  void dockerProfile_multipleSecretsMissing_singleExceptionListsAll() {
    Environment environment = envWithProfiles("docker");
    when(environment.getProperty("wastepilot.sensor.api-key", "")).thenReturn("");

    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET), // bad JWT
        geminiProps(REAL_GEMINI_KEY),
        environment,                    // bad sensor key
        ""                              // bad audit key
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("AUDIT_CHAIN_HMAC_KEY")
        .hasMessageContaining("SENSOR_INGEST_API_KEY")
        .hasMessageContaining("[1]")
        .hasMessageContaining("[2]")
        .hasMessageContaining("[3]");
  }

  @Test
  void dockerProfile_jwtAndAuditMissing_messageContainsBoth() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET), // bad JWT
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker"),      // sensor key OK
        ""                              // bad audit key
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("AUDIT_CHAIN_HMAC_KEY");
  }
}

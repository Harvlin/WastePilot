package com.project.wastepilot.security;

import com.project.wastepilot.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

import static com.project.wastepilot.security.SecurityStartupValidator.LOCAL_DEV_JWT_SECRET;
import static com.project.wastepilot.security.SecurityStartupValidator.MOCK_GEMINI_KEY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link SecurityStartupValidator}.
 * No Spring context — uses Mockito only to keep the suite fast.
 */
@ExtendWith(MockitoExtension.class)
class SecurityStartupValidatorTest {

  private static final String REAL_JWT_SECRET = "super-secret-production-key-abcdef1234567890";
  private static final String REAL_GEMINI_KEY = "AIzaSyD-real-key-example";
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

  private static Environment envWithProfiles(String... profiles) {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(profiles);
    lenient().when(env.getProperty("wastepilot.sensor.api-key", "")).thenReturn("test-sensor-key");
    return env;
  }

  // ── Item 1: docker profile + default JWT secret → must throw ────────────────

  @Test
  void dockerProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker")
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET must be explicitly set in production");
  }

  @Test
  void prodProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("prod")
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET must be explicitly set in production");
  }

  // ── Item 2: docker profile + real JWT secret → must NOT throw ───────────────

  @Test
  void dockerProfile_realJwtSecret_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles("docker")
    );

    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── Item 3: local profile + default secret → must NOT throw ─────────────────

  @Test
  void localProfile_defaultJwtSecret_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("local")
    );

    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  @Test
  void testProfile_defaultJwtSecret_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("test")
    );

    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── Item 3: no active profile + default secret → must NOW throw (fixed behavior) ─

  @Test
  void noActiveProfile_defaultJwtSecret_throwsIllegalState() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(LOCAL_DEV_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles() // no profiles active — NOT in safe set, must reject
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET must be explicitly set");
  }

  @Test
  void noActiveProfile_realJwtSecret_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        envWithProfiles() // no profiles — but real secret provided, so fine
    );
    // sensor key mock is provided via envWithProfiles helper
    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  // ── Item 4: mock Gemini key in docker profile → does NOT throw (warning only) ─

  @Test
  void dockerProfile_mockGeminiKey_realJwt_doesNotThrow() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(MOCK_GEMINI_KEY),
        envWithProfiles("docker")
    );

    // Warning-level log only — must not throw
    assertThatCode(() -> validator.run(NO_ARGS)).doesNotThrowAnyException();
  }

  @Test
  void dockerProfile_missingSensorApiKey_throwsIllegalState() {
    Environment environment = envWithProfiles("docker");
    when(environment.getProperty("wastepilot.sensor.api-key", "")).thenReturn("");
    SecurityStartupValidator validator = new SecurityStartupValidator(
        jwtProps(REAL_JWT_SECRET),
        geminiProps(REAL_GEMINI_KEY),
        environment
    );

    assertThatThrownBy(() -> validator.run(NO_ARGS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SENSOR_INGEST_API_KEY");
  }
}

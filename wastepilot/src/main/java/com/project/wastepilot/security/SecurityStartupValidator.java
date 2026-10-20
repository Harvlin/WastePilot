package com.project.wastepilot.security;

import com.project.wastepilot.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

  static final String LOCAL_DEV_JWT_SECRET =
      "wastepilot-local-dev-secret-key-that-is-at-least-256-bits-long";
  static final String MOCK_GEMINI_KEY = "mock-gemini-key-for-local-dev";

  /**
   * Profiles where the insecure dev-secret default is explicitly permitted.
   * Anything outside this set (including no profile at all) must have a real secret.
   */
  private static final Set<String> SAFE_LOCAL_PROFILES = Set.of("local", "test");

  private final JwtProperties jwtProperties;
  private final GeminiProperties geminiProperties;
  private final Environment environment;

  @Override
  public void run(ApplicationArguments args) {
    String[] activeProfiles = environment.getActiveProfiles();

    boolean isSafeLocalProfile = Arrays.stream(activeProfiles)
        .anyMatch(SAFE_LOCAL_PROFILES::contains);

    if (isSafeLocalProfile) {
      // local/test — dev defaults are fine; skip further checks.
      log.info("[SECURITY] Running under local/test profile — startup secret checks skipped.");
      return;
    }

    // --- JWT Secret check (hard failure for any non-local/non-test profile) ---
    if (LOCAL_DEV_JWT_SECRET.equals(jwtProperties.secret())) {
      throw new IllegalStateException(
          "[SECURITY] JWT_SECRET must be explicitly set in production — refusing to start with the " +
          "default development secret. Active profiles: " + Arrays.toString(activeProfiles) + ". " +
          "Set the JWT_SECRET environment variable to a securely generated value " +
          "(e.g. openssl rand -base64 32). See .env.example for reference."
      );
    }

    if (MOCK_GEMINI_KEY.equals(geminiProperties.apiKey())) {
      log.warn(
          "[SECURITY] GEMINI_API_KEY is still set to the mock development value. " +
          "OCR-based inventory scanning will not function correctly. " +
          "Set GEMINI_API_KEY to a real Google AI Studio key if OCR is required in this environment."
      );
    }

    String sensorApiKey = java.util.Objects.requireNonNullElse(
        environment.getProperty("wastepilot.sensor.api-key", ""), "").trim();
    if (sensorApiKey.isEmpty() || sensorApiKey.startsWith("REPLACE_WITH_")) {
      throw new IllegalStateException(
          "[SECURITY] SENSOR_INGEST_API_KEY must be explicitly set in protected profiles."
      );
    }

    log.info("[SECURITY] Startup secret validation passed for profile(s): {}",
        Arrays.toString(activeProfiles));
  }
}

package com.project.wastepilot.security;

import com.project.wastepilot.config.GeminiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Centralised fail-fast validator for all required production secrets.
 *
 * <p>Runs as an {@link ApplicationRunner} after the Spring context is fully refreshed.
 * In any profile outside {@code local} / {@code test} it checks every environment-variable-
 * backed secret that the application requires and collects <em>all</em> failures before
 * throwing, so that a deploy log shows every missing variable at once rather than requiring
 * a separate redeploy cycle to discover each one.
 *
 * <p>Secrets validated here (hard failure unless noted):
 * <ul>
 *   <li>{@code JWT_SECRET} — must not be the dev default</li>
 *   <li>{@code AUDIT_CHAIN_HMAC_KEY} — must be non-blank and not the dev default</li>
 *   <li>{@code SENSOR_INGEST_API_KEY} — must be non-blank and not a placeholder</li>
 *   <li>{@code GEMINI_API_KEY} — warning-only; OCR degrades but the app still starts</li>
 * </ul>
 *
 * <p>Database credentials, CORS origin, and DB_SSL_MODE are not checked here — Spring's
 * property-binding layer will fail at context refresh time if a required datasource property
 * is absent, which happens before this runner executes.
 */
@Slf4j
@Component
public class SecurityStartupValidator implements ApplicationRunner {

  // ── Dev-only sentinel values ─────────────────────────────────────────────────

  static final String LOCAL_DEV_JWT_SECRET =
      "wastepilot-local-dev-secret-key-that-is-at-least-256-bits-long";

  static final String MOCK_GEMINI_KEY = "mock-gemini-key-for-local-dev";

  /**
   * The dev-only HMAC key also used as the in-code fallback in {@code AuditChainService}.
   * Declared here as well so tests can reference a single source of truth.
   */
  static final String DEV_AUDIT_HMAC_KEY = "wastepilot-audit-dev-hmac-key-for-local-only";

  /**
   * Profiles where the insecure dev-secret defaults are explicitly permitted.
   * Anything outside this set (including no active profile) must have real secrets.
   */
  private static final Set<String> SAFE_LOCAL_PROFILES = Set.of("local", "test");

  // ── Dependencies ─────────────────────────────────────────────────────────────

  private final JwtProperties jwtProperties;
  private final GeminiProperties geminiProperties;
  private final Environment environment;
  private final String auditHmacKey;

  public SecurityStartupValidator(
      JwtProperties jwtProperties,
      GeminiProperties geminiProperties,
      Environment environment,
      @Value("${wastepilot.audit.hmac-key:}") String auditHmacKey
  ) {
    this.jwtProperties = jwtProperties;
    this.geminiProperties = geminiProperties;
    this.environment = environment;
    this.auditHmacKey = auditHmacKey;
  }

  // ── ApplicationRunner ─────────────────────────────────────────────────────────

  @Override
  public void run(ApplicationArguments args) {
    String[] activeProfiles = environment.getActiveProfiles();

    boolean isSafeLocalProfile = Arrays.stream(activeProfiles)
        .anyMatch(SAFE_LOCAL_PROFILES::contains);

    if (isSafeLocalProfile) {
      // local/test — dev defaults are fine; skip all further checks.
      log.info("[SECURITY] Running under local/test profile — startup secret checks skipped.");
      return;
    }

    // Collect every failure before throwing so the deploy log shows all missing variables
    // at once. A single combined exception is thrown only at the end.
    List<String> failures = new ArrayList<>();

    // --- JWT Secret (hard failure) ---
    if (LOCAL_DEV_JWT_SECRET.equals(jwtProperties.secret())) {
      failures.add(
          "JWT_SECRET is set to the development default. " +
          "Generate a real value with: openssl rand -base64 32"
      );
    }

    // --- Audit chain HMAC key (hard failure) ---
    // Defense-in-depth: AuditChainService also checks this in its constructor, but a missing
    // key there throws during bean construction and only appears for the first missing secret.
    // Checking here ensures all failures are visible together before any bean is constructed.
    String normalizedAuditKey = Objects.requireNonNullElse(auditHmacKey, "").trim();
    if (normalizedAuditKey.isEmpty()
        || normalizedAuditKey.startsWith("REPLACE_WITH_")
        || DEV_AUDIT_HMAC_KEY.equals(normalizedAuditKey)) {
      failures.add(
          "AUDIT_CHAIN_HMAC_KEY is missing or set to the development default. " +
          "Generate a real value with: openssl rand -hex 32"
      );
    }

    // --- Sensor ingest API key (hard failure) ---
    String sensorApiKey = Objects.requireNonNullElse(
        environment.getProperty("wastepilot.sensor.api-key", ""), "").trim();
    if (sensorApiKey.isEmpty() || sensorApiKey.startsWith("REPLACE_WITH_")) {
      failures.add(
          "SENSOR_INGEST_API_KEY is missing or set to a placeholder value."
      );
    }

    // --- Gemini API key (warning only — OCR degrades but the app still starts) ---
    if (MOCK_GEMINI_KEY.equals(geminiProperties.apiKey())) {
      log.warn(
          "[SECURITY] GEMINI_API_KEY is still set to the mock development value. " +
          "OCR-based inventory scanning will not function correctly. " +
          "Set GEMINI_API_KEY to a real Google AI Studio key if OCR is required in this environment."
      );
    }

    // --- Report all failures together ----------------------------------------
    if (!failures.isEmpty()) {
      StringBuilder message = new StringBuilder();
      message.append("[SECURITY] Application refused to start — ")
             .append(failures.size())
             .append(" required environment variable(s) are missing or set to insecure defaults ")
             .append("in profile(s): ").append(Arrays.toString(activeProfiles)).append(".\n")
             .append("Fix ALL of the following before redeploying:\n");
      for (int i = 0; i < failures.size(); i++) {
        message.append("  [").append(i + 1).append("] ").append(failures.get(i)).append("\n");
      }
      message.append("See wastepilot/.env.example for the complete required-variable checklist.");
      throw new IllegalStateException(message.toString());
    }

    log.info("[SECURITY] Startup secret validation passed for profile(s): {}",
        Arrays.toString(activeProfiles));
  }
}

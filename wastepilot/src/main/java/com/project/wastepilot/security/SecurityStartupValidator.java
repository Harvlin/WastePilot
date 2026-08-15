package com.project.wastepilot.security;

import com.project.wastepilot.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Startup-time security validator.
 *
 * <p>On production profiles (docker, prod), this component fails fast if either:
 * <ul>
 *   <li>JWT_SECRET is the publicly-known local-dev default — hard failure, prevents insecure token issuance.</li>
 *   <li>GEMINI_API_KEY is the mock default — warning only, degrades OCR feature but does not compromise auth.</li>
 * </ul>
 *
 * <p>The local and test profiles are intentionally excluded so developers can onboard with zero config.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

  static final String LOCAL_DEV_JWT_SECRET =
      "wastepilot-local-dev-secret-key-that-is-at-least-256-bits-long";
  static final String MOCK_GEMINI_KEY = "mock-gemini-key-for-local-dev";

  /** Profiles where insecure defaults must be rejected at startup. */
  private static final String[] PROTECTED_PROFILES = {"docker", "prod"};

  private final JwtProperties jwtProperties;
  private final GeminiProperties geminiProperties;
  private final Environment environment;

  @Override
  public void run(ApplicationArguments args) {
    String[] activeProfiles = environment.getActiveProfiles();

    boolean isProtectedProfile = Arrays.stream(activeProfiles)
        .anyMatch(p -> Arrays.asList(PROTECTED_PROFILES).contains(p));

    if (!isProtectedProfile) {
      // Local / test development — skip checks entirely for zero-friction onboarding.
      return;
    }

    // --- JWT Secret check (hard failure) ---
    if (LOCAL_DEV_JWT_SECRET.equals(jwtProperties.secret())) {
      throw new IllegalStateException(
          "[SECURITY] JWT_SECRET must be explicitly set in production — refusing to start with the " +
          "default development secret. Set the JWT_SECRET environment variable to a securely " +
          "generated value (e.g. openssl rand -base64 32). " +
          "See .env.example for reference."
      );
    }

    // --- Gemini API Key check (warning only) ---
    if (MOCK_GEMINI_KEY.equals(geminiProperties.apiKey())) {
      log.warn(
          "[SECURITY] GEMINI_API_KEY is still set to the mock development value. " +
          "OCR-based inventory scanning will not function correctly. " +
          "Set GEMINI_API_KEY to a real Google AI Studio key if OCR is required in this environment."
      );
    }

    log.info("[SECURITY] Startup secret validation passed for profile(s): {}",
        Arrays.toString(activeProfiles));
  }
}

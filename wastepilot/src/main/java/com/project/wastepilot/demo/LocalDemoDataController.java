package com.project.wastepilot.demo;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing local-only demo data endpoints.
 *
 * <p>Strictly gated behind the {@code local} profile. Never available in Docker/Production.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDemoDataController {

  private final LocalDemoDataSeederService seederService;

  @PostMapping("/seed")
  public ResponseEntity<LocalDemoDataResponse> seedDemoData() {
    log.info("[LOCAL DEV] Received request to seed realistic demo data.");
    LocalDemoDataResponse response = seederService.seedData();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/reset")
  public ResponseEntity<Map<String, String>> resetDemoData() {
    log.info("[LOCAL DEV] Received request to reset demo data.");
    seederService.resetData();
    return ResponseEntity.ok(Map.of(
        "status", "reset_completed",
        "message", "Database successfully reset to a clean empty state."
    ));
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getDevStatus() {
    seederService.assertLocalProfileOnly();
    return ResponseEntity.ok(Map.of(
        "status", "active",
        "profile", "local",
        "safetyGuard", "enforced",
        "message", "Local demo seeding tools are active and ready."
    ));
  }
}

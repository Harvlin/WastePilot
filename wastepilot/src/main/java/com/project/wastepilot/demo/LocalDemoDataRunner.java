package com.project.wastepilot.demo;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runner that executes seeding on startup if {@code wastepilot.demo.seed-on-startup=true}
 * or if the {@code --seed-demo-data} CLI flag is passed.
 *
 * <p>Strictly gated behind the {@code local} profile.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDemoDataRunner implements ApplicationRunner {

  private final LocalDemoDataSeederService seederService;

  @Value("${wastepilot.demo.seed-on-startup:false}")
  private boolean seedOnStartup;

  @Override
  public void run(ApplicationArguments args) {
    boolean hasCliFlag = args.containsOption("seed-demo-data")
        || Arrays.asList(args.getSourceArgs()).contains("--seed-demo-data");

    if (seedOnStartup || hasCliFlag) {
      log.info("[DEMO-SEED] Triggering demo data seed via startup configuration / CLI flag...");
      seederService.seedData();
      log.info("[DEMO-SEED] Auto-seeding on startup finished.");
    }
  }
}

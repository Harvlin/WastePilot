package com.project.wastepilot.domain.dto.settings;

public record UserSettingsResponse(
  String company,
  String email,
  String role,
  Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyRecommendations,
  boolean notifyOverdueBatches
) {}

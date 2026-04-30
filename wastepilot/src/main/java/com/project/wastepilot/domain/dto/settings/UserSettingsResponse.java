package com.project.wastepilot.domain.dto.settings;

public record UserSettingsResponse(
  String companyName,
  String email,
  String role,
  String timezone,
  Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyInsights
) {}

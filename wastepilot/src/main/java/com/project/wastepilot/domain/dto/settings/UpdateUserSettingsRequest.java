package com.project.wastepilot.domain.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record UpdateUserSettingsRequest(
  @NotBlank String company,
  @Email @NotBlank String email,
  @NotBlank String role,
  @Min(1) @Max(100000) Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyRecommendations,
  boolean notifyOverdueBatches
) {}

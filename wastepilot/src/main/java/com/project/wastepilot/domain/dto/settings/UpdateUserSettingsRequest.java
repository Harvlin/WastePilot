package com.project.wastepilot.domain.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record UpdateUserSettingsRequest(
  @NotBlank @Size(max = 200) String companyName,
  @Email @NotBlank @Size(max = 160) String email,
  @NotBlank @Size(max = 80) String role,
  @NotBlank @Size(max = 80) String timezone,
  @Min(0) @Max(100000) Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyInsights
) {}

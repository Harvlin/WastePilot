package com.project.wastepilot.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@ConfigurationProperties(prefix = "wastepilot.security")
@Validated
public record SecurityProps(
  @NotEmpty List<String> corsAllowedOrigins,
  @Min(1) @Max(50) int maxUploadSizeMb,
  @NotEmpty List<String> allowedUploadContentTypes,
  RateLimit rateLimit
) {
  public record RateLimit(boolean enabled, @Min(1) int requestsPerMinute) {}
}

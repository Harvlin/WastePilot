package com.project.wastepilot.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "wastepilot.security.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secret,
    @Min(300) long accessTokenTtlSeconds
) {
}

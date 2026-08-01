package com.project.wastepilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "wastepilot.ai.gemini")
@Validated
public record GeminiProperties(
    String apiKey,
    String model,
    String baseUrl
) {}

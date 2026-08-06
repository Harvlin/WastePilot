package com.project.wastepilot.domain.dto.sensor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record SensorIngestRequest(
    @NotBlank String batchId,
    @NotBlank String materialName,
    @NotNull @DecimalMin("0.001") @DecimalMax("99999") BigDecimal quantity,
    @NotBlank String unit,
    @NotBlank String sensorType,
    Instant timestamp
) {}

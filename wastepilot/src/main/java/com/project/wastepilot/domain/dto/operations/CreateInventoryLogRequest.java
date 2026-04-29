package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateInventoryLogRequest(
        String batchId,
        @NotBlank String materialName,
        @NotBlank String type,
        @DecimalMin("0.001") BigDecimal quantity,
        @NotBlank String unit,
        @NotBlank String source
) {}

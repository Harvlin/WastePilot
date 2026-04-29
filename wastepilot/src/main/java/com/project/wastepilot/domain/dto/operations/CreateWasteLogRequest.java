package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateWasteLogRequest(
        @NotBlank String batchId,
        @NotBlank String materialName,
        @DecimalMin("0.001") BigDecimal quantityKg,
        @NotBlank String destination
) {}

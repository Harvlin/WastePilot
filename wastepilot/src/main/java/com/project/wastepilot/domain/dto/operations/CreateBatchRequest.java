package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateBatchRequest(
        @NotBlank String templateName,
        @DecimalMin("0.1") BigDecimal outputUnits,
        @DecimalMin("0.0") BigDecimal wasteKg
) {}

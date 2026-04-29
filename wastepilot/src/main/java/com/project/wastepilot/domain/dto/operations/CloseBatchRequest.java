package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CloseBatchRequest(
        @NotBlank String batchId,
        @DecimalMin("0.1") BigDecimal outputUnits,
        String closeReason
) {}

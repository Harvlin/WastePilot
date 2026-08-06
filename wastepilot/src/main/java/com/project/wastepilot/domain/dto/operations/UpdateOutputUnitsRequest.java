package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateOutputUnitsRequest(
    @NotNull(message = "Output units are required")
    BigDecimal outputUnits,
    
    @NotNull(message = "Reason is required")
    String reason
) {}

package com.project.wastepilot.domain.dto.operations;

import java.math.BigDecimal;
import java.time.Instant;

public record BatchResponse(
        String id,
        String templateName,
        Instant startedAt,
        BigDecimal outputUnits,
        BigDecimal wasteKg,
        String status,
        Instant closedAt,
        String closedBy,
        String closeReason
) {}

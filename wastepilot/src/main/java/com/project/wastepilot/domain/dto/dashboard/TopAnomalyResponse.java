package com.project.wastepilot.domain.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record TopAnomalyResponse(
    String id,
    String date,
    String process,
    BigDecimal zScore,
    BigDecimal wasteKg,
    String status,
    String note
) {}

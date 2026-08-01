package com.project.wastepilot.domain.dto.ai;

import java.math.BigDecimal;

public record AnomalyResponse(
    String id,
    String date,
    String process,
    BigDecimal zScore,
    BigDecimal wasteKg,
    String status,
    String note
) {}

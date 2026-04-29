package com.project.wastepilot.domain.dto.operations;

import java.math.BigDecimal;

public record BatchCloseSummaryResponse(
        String batchId,
        BigDecimal outputUnits,
        BigDecimal expectedWasteKg,
        BigDecimal actualWasteKg,
        BigDecimal variance,
        String confidenceLevel,
        BigDecimal confidenceScore,
        BigDecimal landfillShare
) {}

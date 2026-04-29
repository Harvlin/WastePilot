package com.project.wastepilot.domain.dto.analytics;

import java.math.BigDecimal;

public record ReportSummaryResponse(
  BigDecimal circularScoreAvg,
  Integer totalActivities,
  BigDecimal onTimeCloseRate,
  BigDecimal totalInventoryIn,
  BigDecimal totalInventoryOut,
  BigDecimal recoveredWasteKg,
  BigDecimal landfillWasteKg
) {}

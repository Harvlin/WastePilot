package com.project.wastepilot.domain.dto.analytics;

import java.math.BigDecimal;

public record ReportSummaryResponse(
  Integer totalActivities,
  Integer totalBatches,
  Integer completedBatches,
  BigDecimal onTimeCloseRate,
  BigDecimal totalInventoryIn,
  BigDecimal totalInventoryOut,
  BigDecimal totalWasteKg,
  BigDecimal recoveredWasteKg,
  BigDecimal landfillWasteKg,
  BigDecimal circularScoreAvg
) {}

package com.project.wastepilot.domain.dto.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BatchCloseSummaryResponse(
        String batchId,
        String templateName,
        Instant startedAt,
        Boolean overdue,
        BigDecimal plannedInputKg,
        BigDecimal actualInputKg,
        BigDecimal outputUnits,
        BigDecimal wasteTotalKg,
        BigDecimal reuseKg,
        BigDecimal repairKg,
        BigDecimal disposeKg,
        BigDecimal landfillIntensity,
        BigDecimal variancePercent,
        String confidenceLevel,
        Integer confidenceScore,
        ConfidenceBreakdown confidenceBreakdown,
        List<BatchRedFlagResponse> redFlags,
        BigDecimal landfillShare
) {
  public record ConfidenceBreakdown(
      BigDecimal completeness,
      BigDecimal timeliness,
      BigDecimal auditIntegrity
  ) {}

  public record BatchRedFlagResponse(
      String id,
      String batchId,
      String severity,
      String type,
      String message,
      Instant createdAt,
      Instant resolvedAt,
      String resolvedBy
) {}
}

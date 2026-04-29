package com.project.wastepilot.domain.dto.analytics;

import java.util.List;

public record ReportsPayloadResponse(
  String period,
  String windowLabel,
  ReportSummaryResponse summary,
  List<ReportTrendPoint> trend,
  List<ReportTopAction> topActions,
  List<ReportTopContributor> topContributors,
  List<String> highlights
) {}

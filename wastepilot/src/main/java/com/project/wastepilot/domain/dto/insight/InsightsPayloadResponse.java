package com.project.wastepilot.domain.dto.insight;

import java.util.List;

public record InsightsPayloadResponse(
  List<CircularInsightResponse> recommendations,
  List<AnomalyResponse> anomalies
) {}

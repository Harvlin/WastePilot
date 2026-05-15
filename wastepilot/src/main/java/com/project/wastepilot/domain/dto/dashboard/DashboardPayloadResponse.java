package com.project.wastepilot.domain.dto.dashboard;

import java.util.List;

public record DashboardPayloadResponse(
    double circularScore,
    List<CircularMetricResponse> metrics,
    List<WasteTrendPointResponse> wasteTrend,
    List<InsightPreviewResponse> insights,
    TopAnomalyResponse topAnomaly
) {}

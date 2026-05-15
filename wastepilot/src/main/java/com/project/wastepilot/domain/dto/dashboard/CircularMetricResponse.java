package com.project.wastepilot.domain.dto.dashboard;

public record CircularMetricResponse(
    String id,
    String label,
    double value,
    String unit,
    double delta
) {}

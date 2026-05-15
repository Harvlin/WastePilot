package com.project.wastepilot.domain.dto.dashboard;

public record WasteTrendPointResponse(
    String date,
    double input,
    double waste,
    double reused
) {}

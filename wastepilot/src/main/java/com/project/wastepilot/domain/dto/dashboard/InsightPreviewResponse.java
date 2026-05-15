package com.project.wastepilot.domain.dto.dashboard;

import java.time.Instant;

public record InsightPreviewResponse(
    String id,
    String title,
    String content,
    String impactCategory,
    String status,
    Instant createdAt
) {}

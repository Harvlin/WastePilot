package com.project.wastepilot.domain.dto.ai;

import java.time.Instant;

public record InsightResponse(
    String id,
    String title,
    String content,
    String impactCategory,
    String status,
    Instant createdAt
) {}

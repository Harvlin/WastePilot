package com.project.wastepilot.domain.dto.integrity;

import java.math.BigDecimal;

public record PatternReviewEntry(
    String closedBy,
    int suspiciousCloseCount,
    int totalCloseCount,
    BigDecimal suspicionPercent,
    String note
) {}

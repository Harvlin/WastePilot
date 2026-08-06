package com.project.wastepilot.domain.dto.operations;

import java.math.BigDecimal;
import java.time.Instant;

public record FloorViewBatchResponse(
    String batchId,
    String templateName,
    Instant startedAt,
    Long runningTimeMinutes,
    BigDecimal variancePercent,
    String healthIndicator
) {}

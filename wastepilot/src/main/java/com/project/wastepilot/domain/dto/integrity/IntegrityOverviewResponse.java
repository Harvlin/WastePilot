package com.project.wastepilot.domain.dto.integrity;

import java.math.BigDecimal;

public record IntegrityOverviewResponse(
  BigDecimal averageConfidenceScore,
  Integer openRedFlags,
  Integer postScoreEdits,
  Integer overdueBatchClosures
) {}

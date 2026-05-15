package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.analytics.ReportsPayloadResponse;

public interface ReportsService {
  ReportsPayloadResponse getReportsPayload(String period);
}

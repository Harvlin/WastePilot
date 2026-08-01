package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.ai.AnomalyResponse;
import com.project.wastepilot.domain.dto.ai.InsightResponse;
import java.util.List;
import java.util.UUID;

public interface InsightsService {
  List<InsightResponse> getAllInsights();
  InsightResponse updateInsightStatus(UUID id, String status);

  List<AnomalyResponse> getAllAnomalies();
  AnomalyResponse updateAnomalyStatus(UUID id, String status);
}

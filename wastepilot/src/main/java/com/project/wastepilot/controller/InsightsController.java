package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.ai.AnomalyResponse;
import com.project.wastepilot.domain.dto.ai.InsightResponse;
import com.project.wastepilot.domain.dto.ai.UpdateStatusRequest;
import com.project.wastepilot.service.InsightsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class InsightsController {

  private final InsightsService insightsService;

  /** GET /api/v1/ai/insights — all insights ordered newest first */
  @GetMapping("/insights")
  public List<InsightResponse> getInsights() {
    return insightsService.getAllInsights();
  }

  /** PATCH /api/v1/ai/insights/{id}/status — update insight status (new/applied/ignored) */
  @PatchMapping("/insights/{id}/status")
  public InsightResponse updateInsightStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStatusRequest request
  ) {
    return insightsService.updateInsightStatus(id, request.status());
  }

  /** GET /api/v1/ai/anomalies — all anomalies ordered newest first */
  @GetMapping("/anomalies")
  public List<AnomalyResponse> getAnomalies() {
    return insightsService.getAllAnomalies();
  }

  /** PATCH /api/v1/ai/anomalies/{id}/status — update anomaly status */
  @PatchMapping("/anomalies/{id}/status")
  public AnomalyResponse updateAnomalyStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStatusRequest request
  ) {
    return insightsService.updateAnomalyStatus(id, request.status());
  }
}

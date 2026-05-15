package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.analytics.AnalyticsPayloadResponse;
import com.project.wastepilot.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  @GetMapping
  public AnalyticsPayloadResponse getAnalytics() {
    return analyticsService.getAnalyticsPayload();
  }
}

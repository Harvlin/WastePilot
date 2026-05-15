package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.analytics.ReportsPayloadResponse;
import com.project.wastepilot.service.ReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {

  private final ReportsService reportsService;

  /**
   * GET /api/v1/reports?period=weekly
   * GET /api/v1/reports?period=monthly
   */
  @GetMapping
  public ReportsPayloadResponse getReports(
      @RequestParam(name = "period", defaultValue = "weekly") String period
  ) {
    return reportsService.getReportsPayload(period);
  }
}

package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.dashboard.DashboardPayloadResponse;
import com.project.wastepilot.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping
  public DashboardPayloadResponse getDashboard() {
    return dashboardService.getDashboardPayload();
  }
}

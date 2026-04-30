package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.dto.integrity.IntegrityOverviewResponse;
import com.project.wastepilot.service.IntegrityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrity")
@RequiredArgsConstructor
public class IntegrityController {
  private final IntegrityService integrityService;

  @GetMapping("/activity-logs")
  public List<ActivityLogEntryResponse> getActivityLogs(@RequestParam(required = false) String batchId) {
    return integrityService.getActivityLogs(batchId);
  }

  @GetMapping("/audit-trail")
  public List<AuditTrailEntryResponse> getAuditTrail(@RequestParam(required = false) String batchId) {
    return integrityService.getAuditTrail(batchId);
  }

  @GetMapping("/overview")
  public IntegrityOverviewResponse getOverview() {
    return integrityService.getOverview();
  }
}


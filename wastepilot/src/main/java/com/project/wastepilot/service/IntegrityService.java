package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.dto.integrity.IntegrityOverviewResponse;
import java.util.List;

public interface IntegrityService {
    List<ActivityLogEntryResponse> getActivityLogs(String batchId);
    List<AuditTrailEntryResponse> getAuditTrail(String batchId);
    IntegrityOverviewResponse getOverview();
}

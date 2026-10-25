package com.project.wastepilot.demo;

import java.util.Map;

public record LocalDemoDataResponse(
    String status,
    String message,
    Map<String, String> operatorCredentials,
    Map<String, String> supervisorCredentials,
    String runningBatchId,
    Map<String, Object> summary
) {}

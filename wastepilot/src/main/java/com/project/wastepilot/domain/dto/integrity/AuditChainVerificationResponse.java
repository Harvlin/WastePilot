package com.project.wastepilot.domain.dto.integrity;

public record AuditChainVerificationResponse(
    boolean valid,
    long entriesChecked,
    long headSequence,
    Long firstInvalidSequence,
    String message
) {}

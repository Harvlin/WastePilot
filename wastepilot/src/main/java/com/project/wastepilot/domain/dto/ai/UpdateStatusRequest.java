package com.project.wastepilot.domain.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
    @NotBlank String status
) {}

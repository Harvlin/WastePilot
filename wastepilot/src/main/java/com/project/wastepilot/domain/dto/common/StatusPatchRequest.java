package com.project.wastepilot.domain.dto.common;

import jakarta.validation.constraints.NotBlank;

public record StatusPatchRequest(@NotBlank String status) {}


package com.project.wastepilot.domain.dto.operations;

import jakarta.validation.constraints.NotBlank;

public record RecoverWasteRequest(@NotBlank String wasteLogId) {}


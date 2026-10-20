package com.project.wastepilot.domain.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record LandfillIntensityPoint(@JsonProperty("week") String batchLabel, BigDecimal kgPerUnit) {}

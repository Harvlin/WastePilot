package com.project.wastepilot.domain.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CircularityPoint(@JsonProperty("week") String batchLabel, BigDecimal score) {}

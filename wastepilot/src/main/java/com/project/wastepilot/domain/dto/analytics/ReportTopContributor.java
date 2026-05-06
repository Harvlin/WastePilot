package com.project.wastepilot.domain.dto.analytics;

import java.time.Instant;

public record ReportTopContributor(
	String actor,
	Integer activities,
	Instant lastSeen
) {}

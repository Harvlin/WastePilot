package com.project.wastepilot.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum InsightStatus {
	NEW,
	applied,
	ignored;

	@JsonValue
	public String toValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	@JsonCreator
	public static InsightStatus fromValue(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		for (InsightStatus status : values()) {
			if (status.name().equalsIgnoreCase(normalized)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown InsightStatus: " + value);
	}
}

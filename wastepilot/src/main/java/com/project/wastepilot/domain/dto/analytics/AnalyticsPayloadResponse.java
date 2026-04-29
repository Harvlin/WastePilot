package com.project.wastepilot.domain.dto.analytics;

import java.util.List;

public record AnalyticsPayloadResponse(
  List<CircularityPoint> circularityTrend,
  List<WasteBreakdownItem> wasteBreakdown,
  List<EfficiencyItem> efficiencyByMaterial,
  List<LandfillSharePoint> landfillShareTrend,
  List<LandfillIntensityPoint> landfillIntensityTrend
) {}

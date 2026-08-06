# Analytics and Reports Honesty Audit (Minggu 3)

During the Minggu 3 Backend Hardening sprint, a full source code audit was conducted on `AnalyticsServiceImpl` and `ReportsServiceImpl` to identify any hidden approximations, hardcoded values, or "stubbed" logic that masqueraded as real data.

## Findings: AnalyticsServiceImpl

The following calculations were evaluated:

1.  **`buildWasteBreakdown`**: **Real**. Sums actual `WasteLog` records by destination.
2.  **`buildEfficiencyByMaterial`**: **Derived**. Calculates efficiency as `(1 - disposed/total) * 100`. This is a reasonable and transparent derived metric.
3.  **`buildCircularityTrend` (per-batch)**: **Derived**. Uses a weighted formula (40% recovery rate, 60% landfill avoidance). The formula is sound but relies on policy-defined weights rather than pure measurement. *Action taken: Added inline documentation for transparency.*
4.  **`buildCircularityTrend` (empty state fallback)**: **Placeholder (Critical Issue)**. Previously returned a single synthetic data point labeled "W1" containing the global average score when no completed batches existed, masquerading as real weekly data. *Action taken: Removed. Now returns an empty list, delegating empty-state rendering to the frontend.*
5.  **`buildLandfillShareTrend` (empty state fallback)**: **Placeholder (Medium Issue)**. Followed the same pattern as above, returning a fake "W1" point with the global landfill share. *Action taken: Removed. Now returns an empty list.*
6.  **`buildLandfillIntensityTrend` (empty state fallback)**: **Placeholder (Critical Issue)**. Returned a hardcoded value of `0.030` labeled as "W1" when no batches existed. This was a completely fabricated data point presented as fact. *Action taken: Removed. Now returns an empty list.*

**Conclusion for Analytics**: All genuine calculations are based on real database aggregations. The issue was entirely contained within the empty-state fallback handlers, which have now been removed in favor of explicit `[]` returns.

## Findings: ReportsServiceImpl

The following calculations were evaluated:

1.  **`buildTrend` (daily/monthly buckets)**: **Real**. Aggregates bucket values (inventory, waste, recovered, landfill) from actual database records filtered by timestamp.
2.  **`buildSummary` (counts & totals)**: **Real**. Sums from `BatchEntity`, `InventoryLog`, and `WasteLog`.
3.  **`onTimeCloseRate`**: **Derived**. Calculates `closedWithin24h / totalClosed`. When `closedBatches` is empty, it defaulted to `100%` without indicating it was an estimation. *Action taken: Added `onTimeCloseRateIsEstimated` boolean flag to the response payload to allow the UI to indicate the fallback nature of this value.*
4.  **`circularScoreAvg`**: **Derived**. Calculates the average of non-zero per-bucket scores. The math is sound and derived from real data.
5.  **`buildTopActions` & `buildTopContributors`**: **Real**. Standard aggregations over `ActivityLog` records.

**Conclusion for Reports**: No fabricated data points were found. The only issue was the lack of transparency around the `100%` fallback for on-time close rate when no data exists, which is now explicitly flagged.

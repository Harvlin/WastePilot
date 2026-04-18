import { describe, it, expect, vi } from "vitest";

async function loadMockApi() {
  vi.resetModules();
  return import("@/features/internal/mock-api");
}

describe("mock-api integrity and scoring flows", () => {
  it("requires close reason when variance is above threshold", async () => {
    const api = await loadMockApi();

    await api.createWasteLog({
      batchId: "B-102",
      materialName: "Cotton Residue",
      quantityKg: 35,
      destination: "dispose",
      reason: "Unexpected trim loss",
      aiSuggestedAction: "Inspect blade calibration",
      isRepurposed: false,
    });

    await expect(
      api.closeBatch({
        batchId: "B-102",
        outputUnits: 330,
      }),
    ).rejects.toThrow(/requires a close reason/i);
  });

  it("closes batch successfully with reason and returns summary", async () => {
    const api = await loadMockApi();

    await api.createWasteLog({
      batchId: "B-102",
      materialName: "Cotton Residue",
      quantityKg: 20,
      destination: "reuse",
      reason: "Pattern cuts",
      aiSuggestedAction: "Repurpose into accessory line",
      isRepurposed: true,
    });

    const summary = await api.closeBatch({
      batchId: "B-102",
      outputUnits: 332,
      closeReason: "Final line count confirmed by supervisor",
    });

    expect(summary.batchId).toBe("B-102");
    expect(summary.outputUnits).toBe(332);
    expect(["high", "medium", "low"]).toContain(summary.confidenceLevel);

    const operations = await api.fetchOperationsPayload();
    const closed = operations.batches.find((item) => item.id === "B-102");
    expect(closed?.status).toBe("completed");
    expect(closed?.closedAt).toBeTruthy();
  });

  it("flags post-score edits in audit trail", async () => {
    const api = await loadMockApi();

    await api.closeBatch({
      batchId: "B-102",
      outputUnits: 340,
      closeReason: "Closing with verified count",
    });

    await api.createWasteLog({
      batchId: "B-102",
      materialName: "Late recorded trim",
      quantityKg: 2,
      destination: "dispose",
      reason: "Delayed operator update",
      aiSuggestedAction: "Review end-of-shift checklist",
      isRepurposed: false,
    });

    const trail = await api.fetchAuditTrail("B-102");
    expect(trail.some((entry) => entry.postScoreEditFlag)).toBe(true);
  });

  it("provides integrity overview and bounded dashboard score", async () => {
    const api = await loadMockApi();

    const [integrity, dashboard] = await Promise.all([
      api.fetchIntegrityOverview(),
      api.fetchDashboardPayload(),
    ]);

    expect(integrity.averageConfidenceScore).toBeGreaterThanOrEqual(0);
    expect(integrity.averageConfidenceScore).toBeLessThanOrEqual(100);
    expect(integrity.openRedFlags).toBeGreaterThanOrEqual(0);

    expect(dashboard.circularScore).toBeGreaterThanOrEqual(0);
    expect(dashboard.circularScore).toBeLessThanOrEqual(100);
  });

  it("returns landfill trends in analytics payload", async () => {
    const api = await loadMockApi();
    const analytics = await api.fetchAnalytics();

    expect(analytics.landfillShareTrend.length).toBeGreaterThan(0);
    expect(analytics.landfillIntensityTrend.length).toBeGreaterThan(0);
    expect(analytics.wasteBreakdown.some((item) => item.category === "Landfill")).toBe(true);
  });

  it("supports activity log filtering by batch id", async () => {
    const api = await loadMockApi();

    const [allLogs, scopedLogs] = await Promise.all([
      api.fetchActivityLogs(),
      api.fetchActivityLogs("B-102"),
    ]);

    expect(allLogs.length).toBeGreaterThan(0);
    expect(scopedLogs.every((log) => log.batchId === "B-102")).toBe(true);
  });
});

import { describe, it, expect, vi } from "vitest";

async function loadMockApi() {
  vi.resetModules();
  return import("@/features/internal/mock-api");
}

describe("mock-api integrity and scoring flows", () => {
  it("converts reuse waste into inventory IN with traceability", async () => {
    const api = await loadMockApi();

    const waste = await api.createWasteLog({
      batchId: "B-102",
      materialName: "Offcut Cotton",
      quantityKg: 6,
      destination: "reuse",
      reason: "Reusable panel offcuts",
      aiSuggestedAction: "Convert to accessory inventory",
      isRepurposed: true,
    });

    const recovered = await api.recoverWasteToInventory({ wasteLogId: waste.id });

    expect(recovered.wasteLog.recoveryStatus).toBe("converted");
    expect(recovered.inventoryLog.type).toBe("IN");
    expect(recovered.inventoryLog.source).toBe("Recovered");
    expect(recovered.inventoryLog.recoveryWasteLogId).toBe(waste.id);
  });

  it("blocks duplicate recovery conversion and dispose conversion", async () => {
    const api = await loadMockApi();

    const reusable = await api.createWasteLog({
      batchId: "B-102",
      materialName: "Sleeve offcut",
      quantityKg: 3,
      destination: "repair",
      reason: "Minor repair inventory",
      aiSuggestedAction: "Move to repair stock",
      isRepurposed: false,
    });

    await api.recoverWasteToInventory({ wasteLogId: reusable.id });

    await expect(api.recoverWasteToInventory({ wasteLogId: reusable.id })).rejects.toThrow(
      /already been converted/i,
    );

    const disposed = await api.createWasteLog({
      batchId: "B-102",
      materialName: "Mixed fiber dust",
      quantityKg: 2,
      destination: "dispose",
      reason: "Cannot be reused",
      aiSuggestedAction: "Safe disposal",
      isRepurposed: false,
    });

    await expect(api.recoverWasteToInventory({ wasteLogId: disposed.id })).rejects.toThrow(
      /cannot be converted/i,
    );
  });

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

  it("builds weekly and monthly reports payloads from mock activity data", async () => {
    const api = await loadMockApi();

    const [weekly, monthly] = await Promise.all([
      api.fetchReportsPayload("weekly"),
      api.fetchReportsPayload("monthly"),
    ]);

    expect(weekly.period).toBe("weekly");
    expect(monthly.period).toBe("monthly");
    expect(weekly.trend.length).toBe(7);
    expect(monthly.trend.length).toBe(6);
    expect(weekly.summary.totalActivities).toBeGreaterThanOrEqual(0);
    expect(monthly.summary.circularScoreAvg).toBeGreaterThanOrEqual(0);
    expect(weekly.topActions.length).toBeGreaterThanOrEqual(0);
  });
});

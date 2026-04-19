import {
  ActivityLogEntry,
  AnalyticsPayload,
  Anomaly,
  AuditTrailEntry,
  BatchCloseSummary,
  BatchRedFlag,
  CircularInsight,
  DashboardPayload,
  IntegrityOverview,
  InventoryLog,
  Material,
  OcrMaterialLine,
  OperationsPayload,
  ProductionBatch,
  ProductionTemplate,
  ReportPeriod,
  ReportsPayload,
  UserSettings,
  WasteLog,
} from "@/features/internal/types";
import { CLOSE_VARIANCE_THRESHOLD } from "@/features/internal/constants";

const delay = (ms = 600) => new Promise((resolve) => setTimeout(resolve, ms));
const OVERDUE_HOURS = 24;

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function round(value: number, precision = 2) {
  const factor = 10 ** precision;
  return Math.round(value * factor) / factor;
}

function nowIso() {
  return new Date().toISOString();
}

function hoursSince(iso: string) {
  return (Date.now() - new Date(iso).getTime()) / (1000 * 60 * 60);
}

const INSIGHTS_STORAGE_KEY = "wastepilot_mock_insights";
const ANOMALIES_STORAGE_KEY = "wastepilot_mock_anomalies";

function readPersisted<T>(key: string): T | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(key);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as T;
  } catch {
    window.localStorage.removeItem(key);
    return null;
  }
}

function persistMockState<T>(key: string, payload: T) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(key, JSON.stringify(payload));
}

const defaultInsightsStore: CircularInsight[] = [
  {
    id: "ins-1",
    title: "Repurpose cotton offcuts",
    content: "Route cotton offcuts from Batch B-102 into tote-handle components for next Tuesday run.",
    impactCategory: "environment",
    status: "new",
    createdAt: "2026-03-28T08:30:00Z",
  },
  {
    id: "ins-2",
    title: "Tune cutter calibration",
    content: "Reduce cutter speed by 6% in line A to drop edge-trim waste without affecting throughput.",
    impactCategory: "operations",
    status: "applied",
    createdAt: "2026-03-27T13:40:00Z",
  },
  {
    id: "ins-3",
    title: "Shift supplier mix",
    content: "Increase SourceTex lot 14 share to 40%; it generated 18% less scrap than current baseline.",
    impactCategory: "cost",
    status: "ignored",
    createdAt: "2026-03-26T10:15:00Z",
  },
];

const defaultAnomaliesStore: Anomaly[] = [
  {
    id: "ano-1",
    date: "2026-03-28",
    process: "Cutting Line A",
    zScore: 3.2,
    wasteKg: 42,
    status: "new",
    note: "Waste spike detected above normal variance; inspect blade wear and pressure alignment.",
  },
  {
    id: "ano-2",
    date: "2026-03-24",
    process: "Pressing Unit 2",
    zScore: 2.5,
    wasteKg: 27,
    status: "applied",
    note: "Moisture imbalance likely from pre-heat drift; calibration patch applied.",
  },
];

let insightsStore: CircularInsight[] = readPersisted<CircularInsight[]>(INSIGHTS_STORAGE_KEY) ?? defaultInsightsStore;

let anomaliesStore: Anomaly[] = readPersisted<Anomaly[]>(ANOMALIES_STORAGE_KEY) ?? defaultAnomaliesStore;

let batchesStore: ProductionBatch[] = [
  {
    id: "B-102",
    templateName: "Plain Tee v2",
    startedAt: "2026-03-29T08:00:00Z",
    outputUnits: 350,
    wasteKg: 18,
    status: "running",
  },
  {
    id: "B-101",
    templateName: "Accessory Pouch",
    startedAt: "2026-03-28T09:15:00Z",
    outputUnits: 210,
    wasteKg: 9,
    status: "completed",
  },
];

let inventoryStore: InventoryLog[] = [
  {
    id: "INV-1",
    materialName: "Cotton Roll 280gsm",
    type: "IN",
    quantity: 120,
    unit: "kg",
    source: "OCR",
    timestamp: "2026-03-29T07:25:00Z",
  },
  {
    id: "INV-2",
    batchId: "B-102",
    materialName: "Dye Pigment Blue",
    type: "OUT",
    quantity: 16,
    unit: "kg",
    source: "Manual",
    timestamp: "2026-03-29T10:30:00Z",
  },
];

let wasteStore: WasteLog[] = [
  {
    id: "W-1",
    batchId: "B-102",
    materialName: "Cotton Trim",
    quantityKg: 11,
    destination: "reuse",
    reason: "Pattern edge cuts",
    aiSuggestedAction: "Use for drawstring pouches in accessory line.",
    isRepurposed: true,
    recoveryStatus: "pending",
    timestamp: "2026-03-29T11:20:00Z",
  },
  {
    id: "W-2",
    batchId: "B-101",
    materialName: "Mixed Fiber Dust",
    quantityKg: 4,
    destination: "dispose",
    reason: "Unrecoverable blend",
    aiSuggestedAction: "Isolate by blend in next run to improve recyclability.",
    isRepurposed: false,
    recoveryStatus: "not-applicable",
    timestamp: "2026-03-28T12:10:00Z",
  },
];

let materialsStore: Material[] = [
  {
    id: "MAT-1",
    name: "Cotton Roll 280gsm",
    category: "Recyclable",
    unit: "kg",
    circularGrade: "A",
    stock: 420,
    supplier: "SourceTex",
  },
  {
    id: "MAT-2",
    name: "Bamboo Fiber Sheet",
    category: "Biodegradable",
    unit: "kg",
    circularGrade: "A",
    stock: 240,
    supplier: "EcoCore",
  },
  {
    id: "MAT-3",
    name: "Elastic Band Mix",
    category: "Composite",
    unit: "kg",
    circularGrade: "C",
    stock: 82,
    supplier: "FlexiParts",
  },
];

let templatesStore: ProductionTemplate[] = [
  {
    id: "TPL-1",
    name: "Plain Tee v2",
    sku: "PT-02",
    expectedWasteKg: 12,
    updatedAt: "2026-03-27T09:00:00Z",
    lines: [
      { materialId: "MAT-1", materialName: "Cotton Roll 280gsm", quantity: 28, unit: "kg" },
      { materialId: "MAT-3", materialName: "Elastic Band Mix", quantity: 2, unit: "kg" },
    ],
  },
  {
    id: "TPL-2",
    name: "Accessory Pouch",
    sku: "AP-10",
    expectedWasteKg: 5,
    updatedAt: "2026-03-26T15:00:00Z",
    lines: [
      { materialId: "MAT-2", materialName: "Bamboo Fiber Sheet", quantity: 16, unit: "kg" },
      { materialId: "MAT-1", materialName: "Cotton Roll 280gsm", quantity: 6, unit: "kg" },
    ],
  },
];

let settingsStore: UserSettings = {
  companyName: "WastePilot Studio",
  email: "ops@wastepilot.io",
  role: "Operations Admin",
  timezone: "Asia/Jakarta",
  notifyAnomalies: true,
  notifyInsights: true,
  dailyTokenBudget: 12000,
};

let activityLogsStore: ActivityLogEntry[] = [
  {
    id: "ACT-1",
    batchId: "B-102",
    actor: "operator.sari",
    action: "batch_started",
    entity: "batch",
    entityId: "B-102",
    source: "manual",
    timestamp: "2026-03-29T08:01:00Z",
    details: "Started with template Plain Tee v2.",
  },
  {
    id: "ACT-2",
    batchId: "B-102",
    actor: "system",
    action: "ocr_ingested",
    entity: "inventory",
    entityId: "INV-1",
    source: "ocr",
    timestamp: "2026-03-29T07:25:00Z",
    details: "Invoice OCR parsed 3 material lines.",
  },
  {
    id: "ACT-3",
    batchId: "B-101",
    actor: "supervisor.ari",
    action: "batch_closed",
    entity: "batch",
    entityId: "B-101",
    source: "manual",
    timestamp: "2026-03-28T13:05:00Z",
    details: "Closed after variance review.",
  },
];

let auditTrailStore: AuditTrailEntry[] = [
  {
    id: "AUD-1",
    batchId: "B-101",
    field: "outputUnits",
    oldValue: "205",
    newValue: "210",
    editedBy: "supervisor.ari",
    editedAt: "2026-03-28T13:03:00Z",
    reason: "Final QC count adjustment.",
    postScoreEditFlag: false,
  },
];

let redFlagStore: BatchRedFlag[] = [
  {
    id: "RF-1",
    batchId: "B-102",
    severity: "medium",
    type: "overdue-close",
    message: "Batch has not been closed within the expected shift window.",
    createdAt: "2026-03-30T10:00:00Z",
  },
];

const scoreSnapshots: Record<string, { score: number; generatedAt: string }> = {
  "B-101": { score: 72, generatedAt: "2026-03-28T13:05:00Z" },
};

function templateByName(name: string) {
  return templatesStore.find((template) => template.name === name);
}

function wasteByBatch(batchId: string) {
  return wasteStore.filter((item) => item.batchId === batchId);
}

function activityForBatch(batchId: string) {
  return activityLogsStore.filter((item) => item.batchId === batchId);
}

function auditForBatch(batchId: string) {
  return auditTrailStore.filter((item) => item.batchId === batchId);
}

function resolveLandfillCap(share: number) {
  if (share > 0.4) return 55;
  if (share > 0.3) return 70;
  if (share > 0.2) return 80;
  return 100;
}

function addActivityLog(entry: Omit<ActivityLogEntry, "id" | "timestamp">) {
  const next: ActivityLogEntry = {
    id: `ACT-${activityLogsStore.length + 1}`,
    timestamp: nowIso(),
    ...entry,
  };
  activityLogsStore = [next, ...activityLogsStore];
}

function addAuditTrail(entry: Omit<AuditTrailEntry, "id" | "editedAt">) {
  const next: AuditTrailEntry = {
    id: `AUD-${auditTrailStore.length + 1}`,
    editedAt: nowIso(),
    ...entry,
  };
  auditTrailStore = [next, ...auditTrailStore];
}

function buildBatchCloseSummary(batchId: string, outputUnitsOverride?: number): BatchCloseSummary {
  const batch = batchesStore.find((item) => item.id === batchId);
  if (!batch) {
    throw new Error("Batch not found.");
  }

  const template = templateByName(batch.templateName);
  const plannedInputKg = round(template ? template.lines.reduce((sum, line) => sum + line.quantity, 0) : Math.max(12, batch.outputUnits * 0.08));
  const wasteForBatch = wasteByBatch(batchId);
  const wasteTotalKg = round(
    wasteForBatch.length
      ? wasteForBatch.reduce((sum, item) => sum + item.quantityKg, 0)
      : Math.max(batch.wasteKg, 0),
  );
  const reuseKg = round(wasteForBatch.filter((item) => item.destination === "reuse").reduce((sum, item) => sum + item.quantityKg, 0));
  const repairKg = round(wasteForBatch.filter((item) => item.destination === "repair").reduce((sum, item) => sum + item.quantityKg, 0));
  const disposeKg = round(wasteForBatch.filter((item) => item.destination === "dispose").reduce((sum, item) => sum + item.quantityKg, 0));
  const landfillShare = wasteTotalKg > 0 ? round(disposeKg / wasteTotalKg, 4) : 0;

  const actualInputFromWaste = plannedInputKg + wasteTotalKg * 0.45;
  const actualInputKg = round(Math.max(plannedInputKg * 0.8, actualInputFromWaste));
  const outputUnits = outputUnitsOverride ?? batch.outputUnits;
  const landfillIntensity = outputUnits > 0 ? round(disposeKg / outputUnits, 4) : round(disposeKg, 4);

  const variancePercent = plannedInputKg > 0
    ? round(((actualInputKg - plannedInputKg) / plannedInputKg) * 100, 2)
    : 0;
  const overdue = batch.status === "running" && hoursSince(batch.startedAt) > OVERDUE_HOURS;

  const hasInventorySignal = inventoryStore.some((item) => item.type === "OUT" && item.batchId === batchId);
  const hasWasteSignal = wasteForBatch.length > 0;
  const hasOutputSignal = outputUnits > 0;

  const completeness = [hasInventorySignal, hasWasteSignal, hasOutputSignal].filter(Boolean).length / 3;
  const timeliness = overdue ? 0.72 : 1;
  const postScoreEdits = auditForBatch(batchId).filter((item) => item.postScoreEditFlag).length;
  const auditIntegrity = clamp(1 - postScoreEdits * 0.15, 0.45, 1);
  const confidenceScore = Math.round((0.5 * completeness + 0.3 * timeliness + 0.2 * auditIntegrity) * 100);
  const confidenceLevel = confidenceScore >= 85 ? "high" : confidenceScore >= 65 ? "medium" : "low";

  const redFlags: BatchRedFlag[] = [];
  if (Math.abs(variancePercent) > CLOSE_VARIANCE_THRESHOLD) {
    redFlags.push({
      id: `RF-S-${batchId}-variance`,
      batchId,
      severity: "medium",
      type: "variance",
      message: `Variance ${variancePercent}% requires reviewer justification on close.`,
      createdAt: nowIso(),
    });
  }
  if (landfillShare > 0.4) {
    redFlags.push({
      id: `RF-S-${batchId}-landfill`,
      batchId,
      severity: "high",
      type: "landfill-risk",
      message: "Landfill share exceeds 40%. Batch score cap will be applied.",
      createdAt: nowIso(),
    });
  }
  if (overdue) {
    redFlags.push({
      id: `RF-S-${batchId}-overdue`,
      batchId,
      severity: "medium",
      type: "overdue-close",
      message: "Batch close is overdue and will reduce confidence.",
      createdAt: nowIso(),
    });
  }
  if (postScoreEdits > 0) {
    redFlags.push({
      id: `RF-S-${batchId}-post-edit`,
      batchId,
      severity: "high",
      type: "post-score-edit",
      message: "Detected edits after score publication.",
      createdAt: nowIso(),
    });
  }

  return {
    batchId,
    templateName: batch.templateName,
    startedAt: batch.startedAt,
    overdue,
    plannedInputKg,
    actualInputKg,
    outputUnits,
    wasteTotalKg,
    reuseKg,
    repairKg,
    disposeKg,
    landfillShare,
    landfillIntensity,
    variancePercent,
    confidenceScore,
    confidenceLevel,
    confidenceBreakdown: {
      completeness: round(completeness, 3),
      timeliness: round(timeliness, 3),
      auditIntegrity: round(auditIntegrity, 3),
    },
    redFlags,
  };
}

function computeBatchFinalScore(summary: BatchCloseSummary) {
  const wasteTotal = Math.max(summary.wasteTotalKg, 0.0001);
  const recoveryRate = clamp((summary.reuseKg + summary.repairKg) / wasteTotal, 0, 1);
  const wasteEfficiency = clamp(1 - summary.wasteTotalKg / Math.max(summary.actualInputKg, 0.0001), 0, 1);
  const landfillAvoidance = clamp(1 - summary.landfillShare, 0, 1);

  const base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);
  const cap = resolveLandfillCap(summary.landfillShare);
  const qualityFactor = clamp(summary.confidenceScore / 100, 0.5, 1);
  const penalty = summary.redFlags.reduce((sum, flag) => {
    if (flag.severity === "high") return sum + 4;
    if (flag.severity === "medium") return sum + 2;
    return sum + 1;
  }, 0);

  return round(clamp(Math.min(base, cap) * qualityFactor - penalty, 0, 100), 1);
}

function computeIntegrityOverviewInternal(): IntegrityOverview {
  const scoredBatchIds = batchesStore
    .filter((batch) => batch.status === "completed")
    .map((batch) => batch.id);

  const summaries = scoredBatchIds.map((batchId) => buildBatchCloseSummary(batchId));
  const averageConfidenceScore = summaries.length
    ? Math.round(summaries.reduce((sum, item) => sum + item.confidenceScore, 0) / summaries.length)
    : 100;

  const postScoreEdits = auditTrailStore.filter((item) => item.postScoreEditFlag).length;
  const overdueBatchClosures = batchesStore.filter((batch) => batch.status === "running" && hoursSince(batch.startedAt) > OVERDUE_HOURS).length;
  const activeStoreFlags = redFlagStore.filter((flag) => !flag.resolvedAt).length;
  const activeSummaryFlags = summaries.reduce((sum, item) => sum + item.redFlags.length, 0);

  return {
    averageConfidenceScore,
    openRedFlags: activeStoreFlags + activeSummaryFlags,
    postScoreEdits,
    overdueBatchClosures,
  };
}

function computeCircularScoreFromCurrentData() {
  const totalWaste = Math.max(0.0001, wasteStore.reduce((sum, item) => sum + item.quantityKg, 0));
  const reuseKg = wasteStore
    .filter((item) => item.destination === "reuse")
    .reduce((sum, item) => sum + item.quantityKg, 0);
  const repairKg = wasteStore
    .filter((item) => item.destination === "repair")
    .reduce((sum, item) => sum + item.quantityKg, 0);
  const disposeKg = wasteStore
    .filter((item) => item.destination === "dispose")
    .reduce((sum, item) => sum + item.quantityKg, 0);

  const materialInput = Math.max(
    150,
    inventoryStore.filter((item) => item.type === "IN").reduce((sum, item) => sum + item.quantity, 0),
  );

  const recoveryRate = clamp((reuseKg + repairKg) / totalWaste, 0, 1);
  const wasteEfficiency = clamp(1 - totalWaste / materialInput, 0, 1);
  const landfillShare = clamp(disposeKg / totalWaste, 0, 1);
  const landfillAvoidance = 1 - landfillShare;

  const base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);
  const cap = resolveLandfillCap(landfillShare);

  const integrity = computeIntegrityOverviewInternal();
  const qualityFactor = clamp(integrity.averageConfidenceScore / 100, 0.5, 1);
  const penalty = integrity.openRedFlags * 0.8 + anomaliesStore.filter((item) => item.status === "new").length * 0.6;

  return {
    value: round(clamp(Math.min(base, cap) * qualityFactor - penalty, 0, 100), 1),
    materialInput: round(materialInput, 1),
    totalWaste: round(totalWaste, 1),
    reuseRatePercent: round(recoveryRate * 100, 1),
    landfillSharePercent: round(landfillShare * 100, 1),
  };
}

function toTimestamp(iso?: string) {
  if (!iso) {
    return NaN;
  }
  return new Date(iso).getTime();
}

function getLatestDataDate() {
  const timestamps = [
    ...activityLogsStore.map((item) => toTimestamp(item.timestamp)),
    ...inventoryStore.map((item) => toTimestamp(item.timestamp)),
    ...wasteStore.map((item) => toTimestamp(item.timestamp)),
    ...batchesStore.map((item) => toTimestamp(item.startedAt)),
    ...batchesStore.map((item) => toTimestamp(item.closedAt)),
  ].filter((item) => Number.isFinite(item));

  if (!timestamps.length) {
    return new Date();
  }

  return new Date(Math.max(...timestamps));
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, value: number) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + value);
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function addMonths(date: Date, value: number) {
  return new Date(date.getFullYear(), date.getMonth() + value, 1);
}

function computeTrendCircularScore(inputKg: number, wasteKg: number, recoveredKg: number, landfillKg: number) {
  if (inputKg <= 0 && wasteKg <= 0) {
    return 0;
  }

  const wasteBase = Math.max(0.0001, wasteKg);
  const safeInput = Math.max(1, inputKg);
  const recoveryRate = clamp(recoveredKg / wasteBase, 0, 1);
  const wasteEfficiency = clamp(1 - wasteKg / safeInput, 0, 1);
  const landfillShare = clamp(landfillKg / wasteBase, 0, 1);
  const landfillAvoidance = 1 - landfillShare;
  const base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);

  return round(clamp(Math.min(base, resolveLandfillCap(landfillShare)), 0, 100), 1);
}

function normalizeActionLabel(action: string) {
  return action.replaceAll("_", " ");
}

function buildReportHighlights(payload: ReportsPayload) {
  const summary = payload.summary;
  const recoveredRate = summary.totalWasteKg > 0
    ? round((summary.recoveredWasteKg / summary.totalWasteKg) * 100, 1)
    : 0;
  const landfillRate = summary.totalWasteKg > 0
    ? round((summary.landfillWasteKg / summary.totalWasteKg) * 100, 1)
    : 0;
  const topAction = payload.topActions[0];

  const highlights = [
    `Recovery reached ${recoveredRate}% with landfill share at ${landfillRate}% (${payload.windowLabel.toLowerCase()}).`,
    topAction ? `Most frequent action: ${topAction.action} (${topAction.count} logs).` : "No dominant action detected in this period.",
    summary.onTimeCloseRate >= 85
      ? `Batch close discipline is healthy at ${summary.onTimeCloseRate}% on-time.`
      : `Batch close discipline needs attention: ${summary.onTimeCloseRate}% on-time.`,
  ];

  return highlights;
}

export async function fetchReportsPayload(period: ReportPeriod): Promise<ReportsPayload> {
  await delay(260);

  const anchorDate = getLatestDataDate();
  const dayAnchor = startOfDay(anchorDate);

  const buckets = period === "weekly"
    ? Array.from({ length: 7 }).map((_, index) => {
        const start = addDays(dayAnchor, index - 6);
        const end = addDays(start, 1);
        return {
          start,
          end,
          label: start.toLocaleDateString("en-US", { weekday: "short" }),
        };
      })
    : Array.from({ length: 6 }).map((_, index) => {
        const start = addMonths(startOfMonth(anchorDate), index - 5);
        const end = addMonths(start, 1);
        return {
          start,
          end,
          label: start.toLocaleDateString("en-US", { month: "short" }),
        };
      });

  const summaryWindowStart = period === "weekly"
    ? buckets[0].start
    : startOfMonth(anchorDate);
  const summaryWindowEnd = period === "weekly"
    ? buckets[buckets.length - 1].end
    : addDays(dayAnchor, 1);

  const inSummaryWindow = (iso?: string) => {
    const ts = toTimestamp(iso);
    return Number.isFinite(ts) && ts >= summaryWindowStart.getTime() && ts < summaryWindowEnd.getTime();
  };

  const summaryActivities = activityLogsStore.filter((item) => inSummaryWindow(item.timestamp));
  const summaryInventory = inventoryStore.filter((item) => inSummaryWindow(item.timestamp));
  const summaryWaste = wasteStore.filter((item) => inSummaryWindow(item.timestamp));
  const summaryBatches = batchesStore.filter((item) => inSummaryWindow(item.startedAt));
  const completedBatches = batchesStore.filter((item) => inSummaryWindow(item.closedAt));

  const onTimeClosed = completedBatches.filter((item) => {
    if (!item.closedAt) {
      return false;
    }
    return toTimestamp(item.closedAt) - toTimestamp(item.startedAt) <= OVERDUE_HOURS * 60 * 60 * 1000;
  });

  const trend = buckets.map((bucket) => {
    const inBucket = (iso?: string) => {
      const ts = toTimestamp(iso);
      return Number.isFinite(ts) && ts >= bucket.start.getTime() && ts < bucket.end.getTime();
    };

    const inventoryIn = inventoryStore
      .filter((item) => inBucket(item.timestamp) && item.type === "IN")
      .reduce((sum, item) => sum + item.quantity, 0);
    const bucketWaste = wasteStore.filter((item) => inBucket(item.timestamp));
    const wasteKg = bucketWaste.reduce((sum, item) => sum + item.quantityKg, 0);
    const recoveredKg = bucketWaste
      .filter((item) => item.destination === "reuse" || item.destination === "repair")
      .reduce((sum, item) => sum + item.quantityKg, 0);
    const landfillKg = bucketWaste
      .filter((item) => item.destination === "dispose")
      .reduce((sum, item) => sum + item.quantityKg, 0);
    const transactions = inventoryStore.filter((item) => inBucket(item.timestamp)).length
      + bucketWaste.length
      + activityLogsStore.filter((item) => inBucket(item.timestamp)).length;

    return {
      label: bucket.label,
      circularScore: computeTrendCircularScore(inventoryIn, wasteKg, recoveredKg, landfillKg),
      wasteKg: round(wasteKg, 2),
      recoveredKg: round(recoveredKg, 2),
      landfillKg: round(landfillKg, 2),
      transactions,
    };
  });

  const topActions = Object.entries(
    summaryActivities.reduce<Record<string, number>>((accumulator, item) => {
      const key = normalizeActionLabel(item.action);
      accumulator[key] = (accumulator[key] ?? 0) + 1;
      return accumulator;
    }, {}),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([action, count]) => ({ action, count }));

  const topContributors = Object.entries(
    summaryActivities.reduce<Record<string, { activities: number; lastSeen: string }>>((accumulator, item) => {
      const existing = accumulator[item.actor];
      if (!existing) {
        accumulator[item.actor] = { activities: 1, lastSeen: item.timestamp };
        return accumulator;
      }

      accumulator[item.actor] = {
        activities: existing.activities + 1,
        lastSeen: new Date(item.timestamp) > new Date(existing.lastSeen) ? item.timestamp : existing.lastSeen,
      };

      return accumulator;
    }, {}),
  )
    .sort((a, b) => b[1].activities - a[1].activities)
    .slice(0, 5)
    .map(([actor, value]) => ({ actor, activities: value.activities, lastSeen: value.lastSeen }));

  const circularScores = trend
    .map((item) => item.circularScore)
    .filter((item) => item > 0);

  const payload: ReportsPayload = {
    period,
    generatedAt: nowIso(),
    windowLabel: period === "weekly"
      ? `Last 7 days ending ${anchorDate.toLocaleDateString("en-US", { month: "short", day: "numeric" })}`
      : `Month to date (${anchorDate.toLocaleDateString("en-US", { month: "long", year: "numeric" })})`,
    summary: {
      totalActivities: summaryActivities.length,
      totalBatches: summaryBatches.length,
      completedBatches: completedBatches.length,
      onTimeCloseRate: completedBatches.length
        ? round((onTimeClosed.length / completedBatches.length) * 100, 1)
        : 100,
      totalInventoryIn: round(summaryInventory.filter((item) => item.type === "IN").reduce((sum, item) => sum + item.quantity, 0), 2),
      totalInventoryOut: round(summaryInventory.filter((item) => item.type === "OUT").reduce((sum, item) => sum + item.quantity, 0), 2),
      totalWasteKg: round(summaryWaste.reduce((sum, item) => sum + item.quantityKg, 0), 2),
      recoveredWasteKg: round(
        summaryWaste
          .filter((item) => item.destination === "reuse" || item.destination === "repair")
          .reduce((sum, item) => sum + item.quantityKg, 0),
        2,
      ),
      landfillWasteKg: round(
        summaryWaste
          .filter((item) => item.destination === "dispose")
          .reduce((sum, item) => sum + item.quantityKg, 0),
        2,
      ),
      circularScoreAvg: circularScores.length
        ? round(circularScores.reduce((sum, item) => sum + item, 0) / circularScores.length, 1)
        : computeCircularScoreFromCurrentData().value,
    },
    trend,
    topActions,
    topContributors,
    highlights: [],
  };

  payload.highlights = buildReportHighlights(payload);

  return payload;
}

export async function fetchDashboardPayload(): Promise<DashboardPayload> {
  await delay();

  const score = computeCircularScoreFromCurrentData();
  const insightPreview = insightsStore.slice(0, 3);

  const latestWaste = wasteStore
    .slice(0, 6)
    .reverse()
    .map((item, index) => {
      const input = 160 + index * 10;
      return {
        date: new Date(item.timestamp).toLocaleDateString("en-US", { weekday: "short" }),
        input,
        waste: round(item.quantityKg + 15),
        reused: round(item.destination === "dispose" ? Math.max(2, item.quantityKg * 0.1) : item.quantityKg * 0.7),
      };
    });

  return {
    circularScore: score.value,
    metrics: [
      { id: "m1", label: "Material Input", value: score.materialInput, unit: "kg", delta: 4.3 },
      { id: "m2", label: "Waste Output", value: score.totalWaste, unit: "kg", delta: -3.2 },
      { id: "m3", label: "Recovery Rate", value: score.reuseRatePercent, unit: "%", delta: 5.4 },
    ],
    wasteTrend: latestWaste.length ? latestWaste : [
      { date: "Mon", input: 180, waste: 39, reused: 24 },
      { date: "Tue", input: 190, waste: 31, reused: 27 },
      { date: "Wed", input: 175, waste: 36, reused: 20 },
      { date: "Thu", input: 205, waste: 34, reused: 28 },
      { date: "Fri", input: 200, waste: 30, reused: 25 },
      { date: "Sat", input: 130, waste: 22, reused: 18 },
    ],
    insights: insightPreview,
    topAnomaly: anomaliesStore[0],
  };
}

export async function fetchOperationsPayload(): Promise<OperationsPayload> {
  await delay();

  return {
    batches: batchesStore,
    inventoryLogs: inventoryStore,
    wasteLogs: wasteStore,
  };
}

export async function createBatch(input: Omit<ProductionBatch, "id" | "startedAt" | "status">) {
  await delay(450);

  const newBatch: ProductionBatch = {
    id: `B-${100 + batchesStore.length + 1}`,
    startedAt: new Date().toISOString(),
    status: "running",
    ...input,
  };

  const template = templateByName(newBatch.templateName);
  if (template) {
    materialsStore = materialsStore.map((material) => {
      const plannedLine = template.lines.find(
        (line) => line.materialId === material.id || line.materialName.trim().toLowerCase() === material.name.trim().toLowerCase(),
      );

      if (!plannedLine) {
        return material;
      }

      return {
        ...material,
        stock: round(Math.max(0, material.stock - plannedLine.quantity), 2),
      };
    });
  }

  batchesStore = [newBatch, ...batchesStore];
  addActivityLog({
    batchId: newBatch.id,
    actor: "operator.session",
    action: "batch_started",
    entity: "batch",
    entityId: newBatch.id,
    source: "manual",
    details: `Started using ${newBatch.templateName}.`,
  });
  return newBatch;
}

export async function createInventoryLog(input: Omit<InventoryLog, "id" | "timestamp">) {
  await delay(300);

  if (input.type === "OUT" && !input.batchId) {
    throw new Error("Batch is required for inventory OUT logs.");
  }

  const newItem: InventoryLog = {
    id: `INV-${inventoryStore.length + 1}`,
    timestamp: new Date().toISOString(),
    ...input,
  };
  inventoryStore = [newItem, ...inventoryStore];
  addActivityLog({
    batchId: newItem.batchId,
    actor: "operator.session",
    action: "inventory_logged",
    entity: "inventory",
    entityId: newItem.id,
    source: newItem.source === "OCR" ? "ocr" : "manual",
    details: `${newItem.type} ${newItem.quantity}${newItem.unit} ${newItem.materialName}${newItem.batchId ? ` for ${newItem.batchId}` : ""}.`,
  });
  return newItem;
}

export async function createWasteLog(input: Omit<WasteLog, "id" | "timestamp">) {
  await delay(300);

  const recoveryStatus = input.destination === "dispose" ? "not-applicable" : "pending";

  const newItem: WasteLog = {
    id: `W-${wasteStore.length + 1}`,
    timestamp: new Date().toISOString(),
    recoveryStatus,
    ...input,
  };
  wasteStore = [newItem, ...wasteStore];

  addActivityLog({
    batchId: newItem.batchId,
    actor: "operator.session",
    action: "waste_logged",
    entity: "waste",
    entityId: newItem.id,
    source: "manual",
    details: `${newItem.quantityKg}kg to ${newItem.destination}.`,
  });

  const scopedBatch = batchesStore.find((item) => item.id === newItem.batchId);
  const postScoreEditFlag = Boolean(scopedBatch?.status === "completed" && scoreSnapshots[newItem.batchId]);
  if (postScoreEditFlag) {
    addAuditTrail({
      batchId: newItem.batchId,
      field: "wasteLog",
      oldValue: "unchanged",
      newValue: `${newItem.quantityKg}kg ${newItem.destination}`,
      editedBy: "operator.session",
      reason: "Post-close correction submitted.",
      postScoreEditFlag,
    });
    redFlagStore = [
      {
        id: `RF-${redFlagStore.length + 1}`,
        batchId: newItem.batchId,
        severity: "high",
        type: "post-score-edit",
        message: "Waste data edited after score publication.",
        createdAt: nowIso(),
      },
      ...redFlagStore,
    ];
  }

  return newItem;
}

export async function recoverWasteToInventory(input: { wasteLogId: string }) {
  await delay(280);

  const wasteLogIndex = wasteStore.findIndex((item) => item.id === input.wasteLogId);
  if (wasteLogIndex < 0) {
    throw new Error("Waste log not found.");
  }

  const wasteLog = wasteStore[wasteLogIndex];
  if (wasteLog.destination === "dispose") {
    throw new Error("Dispose logs cannot be converted to recovered inventory.");
  }

  if (wasteLog.recoveryStatus === "converted") {
    throw new Error("This waste log has already been converted to inventory.");
  }

  const quantity = Math.max(0, wasteLog.quantityKg);
  if (quantity <= 0) {
    throw new Error("Recovered quantity must be greater than zero.");
  }

  const inventoryLog: InventoryLog = {
    id: `INV-${inventoryStore.length + 1}`,
    batchId: wasteLog.batchId,
    materialName: wasteLog.materialName,
    type: "IN",
    quantity,
    unit: "kg",
    source: "Recovered",
    recoveryWasteLogId: wasteLog.id,
    timestamp: nowIso(),
  };

  inventoryStore = [inventoryLog, ...inventoryStore];

  const updatedWasteLog: WasteLog = {
    ...wasteLog,
    recoveryStatus: "converted",
    recoveryInventoryLogId: inventoryLog.id,
    recoveredAt: nowIso(),
    isRepurposed: true,
  };

  wasteStore[wasteLogIndex] = updatedWasteLog;

  addActivityLog({
    batchId: updatedWasteLog.batchId,
    actor: "operator.session",
    action: "waste_recovered_to_inventory",
    entity: "inventory",
    entityId: inventoryLog.id,
    source: "system",
    details: `${updatedWasteLog.materialName} ${quantity}kg converted from ${updatedWasteLog.id}.`,
  });

  addAuditTrail({
    batchId: updatedWasteLog.batchId,
    field: "recoveryStatus",
    oldValue: wasteLog.recoveryStatus ?? "pending",
    newValue: "converted",
    editedBy: "operator.session",
    reason: "Recovered waste converted to inventory IN.",
    postScoreEditFlag: false,
  });

  return {
    wasteLog: updatedWasteLog,
    inventoryLog,
  };
}

export async function fetchBatchCloseSummary(batchId: string): Promise<BatchCloseSummary> {
  await delay(320);
  return buildBatchCloseSummary(batchId);
}

export async function closeBatch(input: {
  batchId: string;
  outputUnits: number;
  closeReason?: string;
}): Promise<BatchCloseSummary> {
  await delay(500);

  const batch = batchesStore.find((item) => item.id === input.batchId);
  if (!batch) {
    throw new Error("Batch not found.");
  }

  if (batch.status !== "running") {
    throw new Error("This batch is already closed.");
  }

  if (!Number.isFinite(input.outputUnits) || input.outputUnits <= 0) {
    throw new Error("Output units must be greater than zero.");
  }

  const preview = buildBatchCloseSummary(input.batchId, input.outputUnits);
  if (Math.abs(preview.variancePercent) > CLOSE_VARIANCE_THRESHOLD && !input.closeReason?.trim()) {
    throw new Error(`Variance above ${CLOSE_VARIANCE_THRESHOLD}% requires a close reason.`);
  }

  const previousOutput = batch.outputUnits;
  const nextCloseReason = input.closeReason?.trim() || "Confirmed by operator.";

  batch.status = "completed";
  batch.outputUnits = input.outputUnits;
  batch.wasteKg = preview.wasteTotalKg;
  batch.closedAt = nowIso();
  batch.closedBy = "operator.session";
  batch.closeReason = nextCloseReason;

  if (previousOutput !== input.outputUnits) {
    addAuditTrail({
      batchId: batch.id,
      field: "outputUnits",
      oldValue: String(previousOutput),
      newValue: String(input.outputUnits),
      editedBy: "operator.session",
      reason: "Final count on batch closure.",
      postScoreEditFlag: false,
    });
  }

  addActivityLog({
    batchId: batch.id,
    actor: "operator.session",
    action: "batch_closed",
    entity: "batch",
    entityId: batch.id,
    source: "manual",
    details: nextCloseReason,
  });

  const summary = buildBatchCloseSummary(batch.id, batch.outputUnits);
  scoreSnapshots[batch.id] = {
    score: computeBatchFinalScore(summary),
    generatedAt: nowIso(),
  };

  return summary;
}

export async function fetchActivityLogs(batchId?: string): Promise<ActivityLogEntry[]> {
  await delay(200);
  if (!batchId) {
    return activityLogsStore;
  }
  return activityForBatch(batchId);
}

export async function fetchAuditTrail(batchId?: string): Promise<AuditTrailEntry[]> {
  await delay(200);
  if (!batchId) {
    return auditTrailStore;
  }
  return auditForBatch(batchId);
}

export async function fetchIntegrityOverview(): Promise<IntegrityOverview> {
  await delay(220);
  return computeIntegrityOverviewInternal();
}

export async function fetchMaterials() {
  await delay();
  return materialsStore;
}

export async function upsertMaterial(material: Material) {
  await delay(400);
  const index = materialsStore.findIndex((m) => m.id === material.id);
  if (index >= 0) {
    materialsStore[index] = material;
  } else {
    materialsStore = [{ ...material, id: `MAT-${materialsStore.length + 1}` }, ...materialsStore];
  }
  return materialsStore;
}

export async function fetchTemplates() {
  await delay();
  return templatesStore;
}

export async function upsertTemplate(template: ProductionTemplate) {
  await delay(450);
  const index = templatesStore.findIndex((t) => t.id === template.id);
  if (index >= 0) {
    templatesStore[index] = { ...template, updatedAt: new Date().toISOString() };
  } else {
    templatesStore = [
      { ...template, id: `TPL-${templatesStore.length + 1}`, updatedAt: new Date().toISOString() },
      ...templatesStore,
    ];
  }
  return templatesStore;
}

export async function fetchInsights() {
  await delay();
  return {
    recommendations: insightsStore,
    anomalies: anomaliesStore,
  };
}

export async function updateInsightStatus(id: string, status: CircularInsight["status"]) {
  await delay(250);
  insightsStore = insightsStore.map((item) => (item.id === id ? { ...item, status } : item));
  persistMockState(INSIGHTS_STORAGE_KEY, insightsStore);
  return { recommendations: insightsStore, anomalies: anomaliesStore };
}

export async function updateAnomalyStatus(id: string, status: CircularInsight["status"]) {
  await delay(250);
  anomaliesStore = anomaliesStore.map((item) => (item.id === id ? { ...item, status } : item));
  persistMockState(ANOMALIES_STORAGE_KEY, anomaliesStore);
  return anomaliesStore;
}

export async function fetchAnalytics(): Promise<AnalyticsPayload> {
  await delay();

  const summarySamples = batchesStore
    .map((batch) => buildBatchCloseSummary(batch.id))
    .slice(0, 5)
    .reverse();

  const landfillShareTrend = summarySamples.map((sample, index) => ({
    week: `W${index + 1}`,
    share: round(sample.landfillShare * 100, 1),
  }));
  const landfillIntensityTrend = summarySamples.map((sample, index) => ({
    week: `W${index + 1}`,
    kgPerUnit: round(sample.landfillIntensity, 3),
  }));

  const circularityTrend = summarySamples.map((sample, index) => ({
    week: `W${index + 1}`,
    score: computeBatchFinalScore(sample),
  }));

  const totalWaste = Math.max(0.0001, wasteStore.reduce((sum, item) => sum + item.quantityKg, 0));
  const wasteByDestination = {
    Reusable: wasteStore.filter((item) => item.destination === "reuse").reduce((sum, item) => sum + item.quantityKg, 0),
    Repair: wasteStore.filter((item) => item.destination === "repair").reduce((sum, item) => sum + item.quantityKg, 0),
    Landfill: wasteStore.filter((item) => item.destination === "dispose").reduce((sum, item) => sum + item.quantityKg, 0),
  };

  return {
    circularityTrend: circularityTrend.length ? circularityTrend : [{ week: "W1", score: 72 }],
    wasteBreakdown: [
      { category: "Reusable", value: round((wasteByDestination.Reusable / totalWaste) * 100, 1) },
      { category: "Repair", value: round((wasteByDestination.Repair / totalWaste) * 100, 1) },
      { category: "Landfill", value: round((wasteByDestination.Landfill / totalWaste) * 100, 1) },
    ],
    efficiencyByMaterial: [
      { material: "Cotton", efficiency: 84 },
      { material: "Bamboo", efficiency: 79 },
      { material: "Elastic", efficiency: 61 },
      { material: "Dye", efficiency: 72 },
    ],
    landfillShareTrend: landfillShareTrend.length ? landfillShareTrend : [{ week: "W1", share: 24 }],
    landfillIntensityTrend: landfillIntensityTrend.length ? landfillIntensityTrend : [{ week: "W1", kgPerUnit: 0.03 }],
  };
}

export async function processOcrImage(file?: File): Promise<OcrMaterialLine[]> {
  await delay(1200);

  if (!file) {
    throw new Error("Please upload an invoice image first.");
  }

  return [
    { id: "ocr-1", materialName: "Cotton Roll 280gsm", quantity: 60, unit: "kg", price: 1290 },
    { id: "ocr-2", materialName: "Bamboo Fiber Sheet", quantity: 35, unit: "kg", price: 980 },
    { id: "ocr-3", materialName: "Natural Dye Pigment", quantity: 12, unit: "kg", price: 420 },
  ];
}

export async function fetchSettings() {
  await delay();
  return settingsStore;
}

export async function saveSettings(input: UserSettings) {
  await delay(500);
  settingsStore = input;
  return settingsStore;
}

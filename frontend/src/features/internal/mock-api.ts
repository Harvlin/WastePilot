import {
  AnalyticsPayload,
  Anomaly,
  CircularInsight,
  DashboardPayload,
  InventoryLog,
  Material,
  OcrMaterialLine,
  OperationsPayload,
  ProductionBatch,
  ProductionTemplate,
  UserSettings,
  WasteLog,
} from "@/features/internal/types";

const delay = (ms = 600) => new Promise((resolve) => setTimeout(resolve, ms));

let insightsStore: CircularInsight[] = [
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

let anomaliesStore: Anomaly[] = [
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

export async function fetchDashboardPayload(): Promise<DashboardPayload> {
  await delay();

  return {
    circularScore: 76,
    metrics: [
      { id: "m1", label: "Material Input", value: 1180, unit: "kg", delta: 8.2 },
      { id: "m2", label: "Waste Output", value: 214, unit: "kg", delta: -4.6 },
      { id: "m3", label: "Reuse Rate", value: 58, unit: "%", delta: 6.1 },
    ],
    wasteTrend: [
      { date: "Mon", input: 180, waste: 39, reused: 24 },
      { date: "Tue", input: 190, waste: 31, reused: 27 },
      { date: "Wed", input: 175, waste: 36, reused: 20 },
      { date: "Thu", input: 205, waste: 34, reused: 28 },
      { date: "Fri", input: 200, waste: 30, reused: 25 },
      { date: "Sat", input: 130, waste: 22, reused: 18 },
    ],
    insights: insightsStore,
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

  batchesStore = [newBatch, ...batchesStore];
  return newBatch;
}

export async function createInventoryLog(input: Omit<InventoryLog, "id" | "timestamp">) {
  await delay(300);
  const newItem: InventoryLog = {
    id: `INV-${inventoryStore.length + 1}`,
    timestamp: new Date().toISOString(),
    ...input,
  };
  inventoryStore = [newItem, ...inventoryStore];
  return newItem;
}

export async function createWasteLog(input: Omit<WasteLog, "id" | "timestamp">) {
  await delay(300);
  const newItem: WasteLog = {
    id: `W-${wasteStore.length + 1}`,
    timestamp: new Date().toISOString(),
    ...input,
  };
  wasteStore = [newItem, ...wasteStore];
  return newItem;
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
  return { recommendations: insightsStore, anomalies: anomaliesStore };
}

export async function updateAnomalyStatus(id: string, status: CircularInsight["status"]) {
  await delay(250);
  anomaliesStore = anomaliesStore.map((item) => (item.id === id ? { ...item, status } : item));
  return anomaliesStore;
}

export async function fetchAnalytics(): Promise<AnalyticsPayload> {
  await delay();
  return {
    circularityTrend: [
      { week: "W1", score: 62 },
      { week: "W2", score: 66 },
      { week: "W3", score: 71 },
      { week: "W4", score: 74 },
      { week: "W5", score: 76 },
    ],
    wasteBreakdown: [
      { category: "Recyclable", value: 48 },
      { category: "Reusable", value: 26 },
      { category: "Landfill", value: 16 },
      { category: "Repair", value: 10 },
    ],
    efficiencyByMaterial: [
      { material: "Cotton", efficiency: 84 },
      { material: "Bamboo", efficiency: 79 },
      { material: "Elastic", efficiency: 61 },
      { material: "Dye", efficiency: 72 },
    ],
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

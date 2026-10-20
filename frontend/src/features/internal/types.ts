export type CircularGrade = "A" | "B" | "C";
export enum BatchStatus {
  Running = "running",
  Completed = "completed",
}
export enum InsightStatus {
  New = "new",
  Applied = "applied",
  Ignored = "ignored",
}
export type WasteDestination = "reuse" | "repair" | "dispose";
export enum WasteRecoveryStatus {
  NotApplicable = "not-applicable",
  Pending = "pending",
  Converted = "converted",
}

export interface CircularMetric {
  id: string;
  label: string;
  value: number;
  unit: string;
  delta: number;
}

export interface WasteTrendPoint {
  date: string;
  input: number;
  waste: number;
  reused: number;
}

export interface WasteBreakdownPoint {
  category: string;
  value: number;
}

export interface CircularInsight {
  id: string;
  title: string;
  content: string;
  impactCategory: "cost" | "operations" | "environment";
  status: InsightStatus;
  createdAt: string;
}

export interface Anomaly {
  id: string;
  date: string;
  process: string;
  zScore: number | null;
  detectionMode: "STATISTICAL_BASELINE" | "PROVISIONAL_THRESHOLD";
  provisionalThresholdKg?: number | null;
  wasteKg: number;
  status: InsightStatus;
  note: string;
}

export interface ProductionBatch {
  id: string;
  templateName: string;
  startedAt: string;
  outputUnits: number;
  wasteKg: number;
  status: BatchStatus;
  closedAt?: string;
  closedBy?: string;
  closeReason?: string;
}

export interface InventoryLog {
  id: string;
  batchId?: string;
  materialName: string;
  type: "IN" | "OUT";
  quantity: number;
  unit: string;
  source: "OCR" | "Manual" | "Recovered";
  recoveryWasteLogId?: string;
  timestamp: string;
}

export interface WasteLog {
  id: string;
  batchId: string;
  materialName: string;
  quantityKg: number;
  destination: WasteDestination;
  reason: string;
  aiSuggestedAction: string;
  isRepurposed: boolean;
  recoveryStatus: WasteRecoveryStatus;
  recoveryInventoryLogId?: string;
  recoveredAt?: string;
  timestamp: string;
}

export interface Material {
  id: string;
  name: string;
  category: "Recyclable" | "Biodegradable" | "Composite" | "Reusable";
  unit: string;
  circularGrade: CircularGrade;
  stock: number;
  supplier: string;
}

export interface TemplateMaterialLine {
  materialId: string;
  materialName: string;
  quantity: number;
  unit: string;
}

export interface ProductionTemplate {
  id: string;
  name: string;
  sku: string;
  lines: TemplateMaterialLine[];
  expectedWasteKg: number;
  updatedAt: string;
}

export interface OcrMaterialLine {
  id: string;
  materialName: string;
  quantity: number;
  unit: string;
  price: number;
}

export interface DashboardPayload {
  circularScore: number;
  metrics: CircularMetric[];
  wasteTrend: WasteTrendPoint[];
  insights: CircularInsight[];
  topAnomaly: Anomaly;
}

export interface OperationsPayload {
  batches: ProductionBatch[];
  inventoryLogs: InventoryLog[];
  wasteLogs: WasteLog[];
}

export interface ActivityLogEntry {
  id: string;
  batchId?: string;
  actor: string;
  action: string;
  entity: "batch" | "inventory" | "waste" | "score" | "system";
  entityId: string;
  source: "ocr" | "manual" | "system";
  timestamp: string;
  details?: string;
}

export interface AuditTrailEntry {
  id: string;
  batchId: string;
  field: string;
  oldValue: string;
  newValue: string;
  editedBy: string;
  editedAt: string;
  reason: string;
}

export interface BatchRedFlag {
  id: string;
  batchId: string;
  severity: "low" | "medium" | "high";
  type: "variance" | "overdue-close" | "landfill-risk";
  message: string;
  createdAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
}

export interface BatchCloseSummary {
  batchId: string;
  templateName: string;
  startedAt: string;
  overdue: boolean;
  plannedInputKg: number;
  actualInputKg: number;
  outputUnits: number;
  wasteTotalKg: number;
  reuseKg: number;
  repairKg: number;
  disposeKg: number;
  landfillShare: number;
  landfillIntensity: number;
  variancePercent: number;
  confidenceScore: number;
  confidenceLevel: "high" | "medium" | "low";
  confidenceBreakdown: {
    completeness: number;
    timeliness: number;
    auditIntegrity: number;
  };
  redFlags: BatchRedFlag[];
}

export interface IntegrityOverview {
  averageConfidenceScore: number;
  openRedFlags: number;
  overdueBatchClosures: number;
}

export interface PatternReviewEntry {
  closedBy: string;
  suspiciousCloseCount: number;
  totalCloseCount: number;
  suspicionPercent: number;
  note: string;
}

export interface AnalyticsPayload {
  circularityTrend: Array<{ batchLabel: string; score: number }>;
  wasteBreakdown: WasteBreakdownPoint[];
  efficiencyByMaterial: Array<{ material: string; efficiency: number }>;
  landfillShareTrend: Array<{ batchLabel: string; share: number }>;
  landfillIntensityTrend: Array<{ batchLabel: string; kgPerUnit: number }>;
}

export interface AuditChainVerification {
  valid: boolean;
  entriesChecked: number;
  headSequence: number;
  firstInvalidSequence: number | null;
  message: string;
}

export interface CrossValidationDiscrepancy {
  batchId: string;
  materialName: string;
  manualTotalKg: number;
  sensorTotalKg: number;
  relativeDiscrepancyPercent: number;
  note: string;
}

export type ReportPeriod = "weekly" | "monthly";

export interface ReportTrendPoint {
  label: string;
  circularScore: number;
  wasteKg: number;
  recoveredKg: number;
  landfillKg: number;
  transactions: number;
}

export interface ReportTopAction {
  action: string;
  count: number;
}

export interface ReportContributor {
  actor: string;
  activities: number;
  lastSeen: string;
}

export interface ReportsPayload {
  period: ReportPeriod;
  generatedAt: string;
  windowLabel: string;
  summary: {
    totalActivities: number;
    totalBatches: number;
    completedBatches: number;
    onTimeCloseRate: number;
    totalInventoryIn: number;
    totalInventoryOut: number;
    totalWasteKg: number;
    recoveredWasteKg: number;
    landfillWasteKg: number;
    circularScoreAvg: number;
  };
  trend: ReportTrendPoint[];
  topActions: ReportTopAction[];
  topContributors: ReportContributor[];
  highlights: string[];
}

export interface UserSettings {
  companyName: string;
  email: string;
  role: string;
  timezone: string;
  notifyAnomalies: boolean;
  notifyInsights: boolean;
  dailyTokenBudget: number;
}

import { BatchCloseSummary } from "@/features/internal/types";

// ---------------------------------------------------------------------------
// 1. Types / Contracts
// ---------------------------------------------------------------------------

export interface SensorIngestRequest {
  machineId: string;
  sensorType: "weight" | "counter";
  value: number;
  unit: string;
  timestamp: string;
}

// 2. Confidence Score Breakdown is an extension of BatchCloseSummary
export interface ExtendedBatchCloseSummary extends BatchCloseSummary {
  confidenceBreakdown: {
    completeness: number;
    timeliness: number;
    auditIntegrity: number;
  };
}

// 3. Role-Based Access is an extension of AuthUser
export interface ExtendedAuthUser {
  id: string;
  fullName: string;
  email: string;
  role: "admin" | "operator" | "viewer";
  permissions: string[];
}

export interface LiveFloorBatch {
  batchId: string;
  templateName: string;
  lineId: string;
  status: "running" | "paused" | "anomalous";
  runningTimeMinutes: number;
  varianceSoFarPercent: number;
  healthIndicator: "green" | "amber" | "red";
}

export interface LiveFloorPayload {
  activeBatches: LiveFloorBatch[];
  totalActiveLines: number;
  lastUpdated: string;
}

export interface NotificationPayload {
  id: string;
  type: "anomaly" | "insight" | "system" | "integrity";
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
  actionUrl?: string;
}

export interface ForecastPoint {
  date: string;
  predictedKg: number;
  lowerBoundKg: number;
  upperBoundKg: number;
}

export interface ForecastPayload {
  target: string;
  predictions: ForecastPoint[];
  keyInputs: string[];
}

export interface SustainabilityImpact {
  co2SavedKg: number;
  landfillDivertedKg: number;
  waterSavedLiters: number;
  equivalents: {
    treesPlanted: number;
    carsOffRoad: number;
  };
  conversionAssumptions: string[];
}

export interface ExportRequest {
  format: "csv" | "pdf";
  type: "batch_summary" | "audit_trail";
  dateRange?: { start: string; end: string };
  batchId?: string;
}

export interface ComparisonSeries {
  name: string;
  data: Array<{
    date: string;
    value: number;
  }>;
}

export interface ComparisonPayload {
  dimension: string;
  series: ComparisonSeries[];
}

// ---------------------------------------------------------------------------
// 2. Mock Data Generators
// ---------------------------------------------------------------------------

const delay = (ms = 600) => new Promise((resolve) => setTimeout(resolve, ms));

export const mockLiveFloorPayload: LiveFloorPayload = {
  activeBatches: [
    {
      batchId: "B-108",
      templateName: "Cotton Tee v3",
      lineId: "LINE-A",
      status: "running",
      runningTimeMinutes: 125,
      varianceSoFarPercent: 2.4,
      healthIndicator: "green",
    },
    {
      batchId: "B-109",
      templateName: "Accessory Pouch",
      lineId: "LINE-B",
      status: "anomalous",
      runningTimeMinutes: 45,
      varianceSoFarPercent: 6.8,
      healthIndicator: "red",
    },
  ],
  totalActiveLines: 2,
  lastUpdated: new Date().toISOString(),
};

export const mockNotifications: NotificationPayload[] = [
  {
    id: "notif-1",
    type: "anomaly",
    title: "High Variance Terdeteksi",
    message: "Line B (Accessory Pouch) mencatat variansi 6.8% dalam 45 menit terakhir.",
    isRead: false,
    createdAt: new Date().toISOString(),
    actionUrl: "/operations",
  },
  {
    id: "notif-2",
    type: "insight",
    title: "Rekomendasi AI Baru",
    message: "Gunakan sisa potongan kapas untuk produksi shift berikutnya.",
    isRead: false,
    createdAt: new Date(Date.now() - 3600000).toISOString(),
    actionUrl: "/insights",
  },
];

export const mockForecastPayload: ForecastPayload = {
  target: "Total Waste",
  predictions: Array.from({ length: 7 }).map((_, i) => {
    const date = new Date();
    date.setDate(date.getDate() + i);
    const base = 40 + Math.random() * 15;
    return {
      date: date.toISOString().split("T")[0],
      predictedKg: Math.round(base),
      lowerBoundKg: Math.round(base * 0.8),
      upperBoundKg: Math.round(base * 1.2),
    };
  }),
  keyInputs: ["Historical volume", "Upcoming schedule", "Material mix"],
};

export const mockSustainabilityImpact: SustainabilityImpact = {
  co2SavedKg: 1240.5,
  landfillDivertedKg: 850.0,
  waterSavedLiters: 4500,
  equivalents: {
    treesPlanted: 52,
    carsOffRoad: 4,
  },
  conversionAssumptions: ["1kg waste diverted = 1.45kg CO2 saved", "1kg cotton reused = 5L water saved"],
};

export const mockComparisonPayload: ComparisonPayload = {
  dimension: "line",
  series: [
    {
      name: "Line A",
      data: Array.from({ length: 7 }).map((_, i) => {
        const date = new Date();
        date.setDate(date.getDate() - (6 - i));
        return {
          date: date.toISOString().split("T")[0],
          value: 80 + Math.random() * 15, // Circular Score
        };
      }),
    },
    {
      name: "Line B",
      data: Array.from({ length: 7 }).map((_, i) => {
        const date = new Date();
        date.setDate(date.getDate() - (6 - i));
        return {
          date: date.toISOString().split("T")[0],
          value: 60 + Math.random() * 20, // Circular Score
        };
      }),
    },
  ],
};

// ---------------------------------------------------------------------------
// 3. API Client Interface & Implementation
// ---------------------------------------------------------------------------

type RequestErrorKind = "http" | "network" | "timeout";

class ApiRequestError extends Error {
  constructor(message: string, readonly kind: RequestErrorKind, readonly status?: number) {
    super(message);
  }
}

export interface NewFeaturesApi {
  ingestSensorData(input: SensorIngestRequest): Promise<{ message: string }>;
  fetchLiveFloor(): Promise<LiveFloorPayload>;
  fetchNotifications(unreadOnly?: boolean): Promise<NotificationPayload[]>;
  markNotificationRead(id: string): Promise<void>;
  fetchForecast(horizonDays?: number, materialId?: string): Promise<ForecastPayload>;
  fetchSustainabilityImpact(): Promise<SustainabilityImpact>;
  exportReport(input: ExportRequest): Promise<Blob>;
  fetchComparison(dimension: string, dateRange: string): Promise<ComparisonPayload>;
}

class MockNewFeaturesApi implements NewFeaturesApi {
  async ingestSensorData(input: SensorIngestRequest) {
    await delay(300);
    return { message: "Payload ingested successfully" };
  }

  async fetchLiveFloor() {
    await delay(500);
    return mockLiveFloorPayload;
  }

  async fetchNotifications(unreadOnly?: boolean) {
    await delay(400);
    return unreadOnly ? mockNotifications.filter((n) => !n.isRead) : mockNotifications;
  }

  async markNotificationRead(id: string) {
    await delay(200);
    const notification = mockNotifications.find(n => n.id === id);
    if (notification) {
      notification.isRead = true;
    }
  }

  async fetchForecast(horizonDays?: number, materialId?: string) {
    await delay(800);
    return mockForecastPayload;
  }

  async fetchSustainabilityImpact() {
    await delay(400);
    return mockSustainabilityImpact;
  }

  async exportReport(input: ExportRequest) {
    await delay(1200);
    // Simulate blob download
    return new Blob(["dummy data"], { type: input.format === "pdf" ? "application/pdf" : "text/csv" });
  }

  async fetchComparison(dimension: string, dateRange: string) {
    await delay(600);
    return mockComparisonPayload;
  }
}

class SpringBootNewFeaturesApi implements NewFeaturesApi {
  constructor(private readonly baseUrl: string) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    // Exact pattern matching internal-api.ts simplified for space
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        // Auth token injection would happen here
        ...(init?.headers ?? {}),
      },
    });

    if (!response.ok) {
      throw new ApiRequestError(`Failed with status ${response.status}`, "http", response.status);
    }
    
    // For blob exports
    if (path.includes("export")) {
      return response.blob() as unknown as T;
    }

    if (response.status === 204) return undefined as T;
    return response.json();
  }

  async ingestSensorData(input: SensorIngestRequest) {
    return this.request<{ message: string }>("/api/v1/operations/sensors/ingest", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchLiveFloor() {
    return this.request<LiveFloorPayload>("/api/v1/operations/live-floor");
  }

  async fetchNotifications(unreadOnly?: boolean) {
    const qs = unreadOnly ? "?unreadOnly=true" : "";
    return this.request<NotificationPayload[]>(`/api/v1/notifications${qs}`);
  }

  async markNotificationRead(id: string) {
    await this.request<void>(`/api/v1/notifications/${id}/read`, { method: "PATCH" });
  }

  async fetchForecast(horizonDays?: number, materialId?: string) {
    const qs = `?horizonDays=${horizonDays || 7}${materialId ? `&materialId=${materialId}` : ""}`;
    return this.request<ForecastPayload>(`/api/v1/ai/forecast${qs}`);
  }

  async fetchSustainabilityImpact() {
    return this.request<SustainabilityImpact>("/api/v1/analytics/sustainability");
  }

  async exportReport(input: ExportRequest) {
    return this.request<Blob>("/api/v1/reports/export", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchComparison(dimension: string, dateRange: string) {
    return this.request<ComparisonPayload>(`/api/v1/analytics/comparison?dimension=${dimension}&dateRange=${dateRange}`);
  }
}

// ---------------------------------------------------------------------------
// 4. Provider Switch Pattern
// ---------------------------------------------------------------------------

const provider = (import.meta.env.VITE_INTERNAL_API_PROVIDER as string | undefined)?.toLowerCase() ?? "mock";
const springBaseUrl = (import.meta.env.VITE_SPRING_API_BASE_URL as string | undefined) ?? "http://localhost:8080";

export const newFeaturesApi: NewFeaturesApi =
  provider === "spring" ? new SpringBootNewFeaturesApi(springBaseUrl) : new MockNewFeaturesApi();

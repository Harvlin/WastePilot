import {
  AnalyticsPayload,
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
import * as mock from "@/features/internal/mock-api";

export interface InsightsPayload {
  recommendations: CircularInsight[];
  anomalies: Array<{
    id: string;
    date: string;
    process: string;
    zScore: number;
    wasteKg: number;
    status: CircularInsight["status"];
    note: string;
  }>;
}

export type CreateBatchInput = Omit<ProductionBatch, "id" | "startedAt" | "status">;
export type CreateInventoryInput = Omit<InventoryLog, "id" | "timestamp">;
export type CreateWasteInput = Omit<WasteLog, "id" | "timestamp">;

export interface InternalApi {
  fetchDashboardPayload(): Promise<DashboardPayload>;
  fetchOperationsPayload(): Promise<OperationsPayload>;
  createBatch(input: CreateBatchInput): Promise<ProductionBatch>;
  createInventoryLog(input: CreateInventoryInput): Promise<InventoryLog>;
  createWasteLog(input: CreateWasteInput): Promise<WasteLog>;
  fetchMaterials(): Promise<Material[]>;
  upsertMaterial(input: Material): Promise<Material[]>;
  fetchTemplates(): Promise<ProductionTemplate[]>;
  upsertTemplate(input: ProductionTemplate): Promise<ProductionTemplate[]>;
  fetchInsights(): Promise<InsightsPayload>;
  updateInsightStatus(id: string, status: CircularInsight["status"]): Promise<InsightsPayload>;
  updateAnomalyStatus(id: string, status: CircularInsight["status"]): Promise<InsightsPayload["anomalies"]>;
  fetchAnalytics(): Promise<AnalyticsPayload>;
  processOcrImage(file?: File): Promise<OcrMaterialLine[]>;
  fetchSettings(): Promise<UserSettings>;
  saveSettings(input: UserSettings): Promise<UserSettings>;
}

class MockInternalApi implements InternalApi {
  fetchDashboardPayload = mock.fetchDashboardPayload;
  fetchOperationsPayload = mock.fetchOperationsPayload;
  createBatch = mock.createBatch;
  createInventoryLog = mock.createInventoryLog;
  createWasteLog = mock.createWasteLog;
  fetchMaterials = mock.fetchMaterials;
  upsertMaterial = mock.upsertMaterial;
  fetchTemplates = mock.fetchTemplates;
  upsertTemplate = mock.upsertTemplate;
  fetchInsights = mock.fetchInsights;
  updateInsightStatus = mock.updateInsightStatus;
  updateAnomalyStatus = mock.updateAnomalyStatus;
  fetchAnalytics = mock.fetchAnalytics;
  processOcrImage = mock.processOcrImage;
  fetchSettings = mock.fetchSettings;
  saveSettings = mock.saveSettings;
}

class SpringBootInternalApi implements InternalApi {
  constructor(private readonly baseUrl: string) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(init?.headers ?? {}),
      },
      ...init,
    });

    if (!response.ok) {
      const fallback = `Request failed with status ${response.status}`;
      const text = await response.text();
      throw new Error(text || fallback);
    }

    return (await response.json()) as T;
  }

  async fetchDashboardPayload() {
    return this.request<DashboardPayload>("/api/v1/dashboard");
  }

  async fetchOperationsPayload() {
    return this.request<OperationsPayload>("/api/v1/operations");
  }

  async createBatch(input: CreateBatchInput) {
    return this.request<ProductionBatch>("/api/v1/operations/batches", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async createInventoryLog(input: CreateInventoryInput) {
    return this.request<InventoryLog>("/api/v1/operations/inventory-logs", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async createWasteLog(input: CreateWasteInput) {
    return this.request<WasteLog>("/api/v1/operations/waste-logs", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchMaterials() {
    return this.request<Material[]>("/api/v1/materials");
  }

  async upsertMaterial(input: Material) {
    if (input.id) {
      await this.request<Material>(`/api/v1/materials/${input.id}`, {
        method: "PUT",
        body: JSON.stringify(input),
      });
      return this.fetchMaterials();
    }

    await this.request<Material>("/api/v1/materials", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return this.fetchMaterials();
  }

  async fetchTemplates() {
    return this.request<ProductionTemplate[]>("/api/v1/templates");
  }

  async upsertTemplate(input: ProductionTemplate) {
    if (input.id) {
      await this.request<ProductionTemplate>(`/api/v1/templates/${input.id}`, {
        method: "PUT",
        body: JSON.stringify(input),
      });
      return this.fetchTemplates();
    }

    await this.request<ProductionTemplate>("/api/v1/templates", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return this.fetchTemplates();
  }

  async fetchInsights() {
    return this.request<InsightsPayload>("/api/v1/ai/insights");
  }

  async updateInsightStatus(id: string, status: CircularInsight["status"]) {
    return this.request<InsightsPayload>(`/api/v1/ai/insights/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  }

  async updateAnomalyStatus(id: string, status: CircularInsight["status"]) {
    return this.request<InsightsPayload["anomalies"]>(`/api/v1/ai/anomaly/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  }

  async fetchAnalytics() {
    return this.request<AnalyticsPayload>("/api/v1/analytics");
  }

  async processOcrImage(file?: File) {
    if (!file) {
      throw new Error("Please upload an invoice image first.");
    }

    const body = new FormData();
    body.append("image", file);

    const response = await fetch(`${this.baseUrl}/api/v1/ai/ocr`, {
      method: "POST",
      credentials: "include",
      body,
    });

    if (!response.ok) {
      const fallback = `OCR request failed with status ${response.status}`;
      const text = await response.text();
      throw new Error(text || fallback);
    }

    return (await response.json()) as OcrMaterialLine[];
  }

  async fetchSettings() {
    return this.request<UserSettings>("/api/v1/settings");
  }

  async saveSettings(input: UserSettings) {
    return this.request<UserSettings>("/api/v1/settings", {
      method: "PUT",
      body: JSON.stringify(input),
    });
  }
}

const provider = (import.meta.env.VITE_INTERNAL_API_PROVIDER as string | undefined)?.toLowerCase() ?? "mock";
const springBaseUrl = (import.meta.env.VITE_SPRING_API_BASE_URL as string | undefined) ?? "http://localhost:8080";

export const internalApi: InternalApi = provider === "spring"
  ? new SpringBootInternalApi(springBaseUrl)
  : new MockInternalApi();

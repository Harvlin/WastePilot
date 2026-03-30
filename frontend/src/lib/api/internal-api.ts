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

const DEFAULT_SPRING_TIMEOUT_MS = 10_000;

export const SPRING_API_ENDPOINTS = {
  dashboard: "/api/v1/dashboard",
  operations: "/api/v1/operations",
  operationBatches: "/api/v1/operations/batches",
  operationInventoryLogs: "/api/v1/operations/inventory-logs",
  operationWasteLogs: "/api/v1/operations/waste-logs",
  materials: "/api/v1/materials",
  templates: "/api/v1/templates",
  insights: "/api/v1/ai/insights",
  anomalies: "/api/v1/ai/anomaly",
  analytics: "/api/v1/analytics",
  ocr: "/api/v1/ai/ocr",
  settings: "/api/v1/settings",
} as const;

function parseBooleanEnv(value: string | undefined, defaultValue: boolean): boolean {
  if (!value) {
    return defaultValue;
  }

  const normalized = value.trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(normalized)) {
    return true;
  }

  if (["0", "false", "no", "off"].includes(normalized)) {
    return false;
  }

  return defaultValue;
}

function parseTimeoutMs(value: string | undefined, defaultValue: number): number {
  if (!value) {
    return defaultValue;
  }

  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return defaultValue;
  }

  return parsed;
}

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

interface SpringApiOptions {
  timeoutMs: number;
  fallbackApi?: InternalApi;
}

class SpringBootInternalApi implements InternalApi {
  private readonly fallbackLogged = new Set<string>();

  constructor(
    private readonly baseUrl: string,
    private readonly options: SpringApiOptions,
  ) {}

  private logFallback(operation: string, error: unknown) {
    if (this.fallbackLogged.has(operation)) {
      return;
    }

    this.fallbackLogged.add(operation);
    // Keep logs low-noise: one warning per operation during a session.
    console.warn(`[internal-api] Spring operation '${operation}' failed. Falling back to mock API.`, error);
  }

  private async withFallback<T>(
    operation: string,
    primaryCall: () => Promise<T>,
    fallbackCall?: () => Promise<T>,
  ): Promise<T> {
    try {
      return await primaryCall();
    } catch (error) {
      if (!fallbackCall) {
        throw error;
      }

      this.logFallback(operation, error);
      return fallbackCall();
    }
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.options.timeoutMs);

    try {
      const isFormData = init?.body instanceof FormData;
      const response = await fetch(`${this.baseUrl}${path}`, {
        credentials: "include",
        headers: {
          ...(isFormData ? {} : { "Content-Type": "application/json" }),
          ...(init?.headers ?? {}),
        },
        ...init,
        signal: controller.signal,
      });

      if (!response.ok) {
        const fallback = `Request failed with status ${response.status}`;
        const text = await response.text();
        throw new Error(text || fallback);
      }

      if (response.status === 204) {
        return undefined as T;
      }

      const contentType = response.headers.get("content-type") ?? "";
      if (!contentType.includes("application/json")) {
        const text = await response.text();
        throw new Error(text || `Expected JSON response from ${path}.`);
      }

      return (await response.json()) as T;
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw new Error(`Request timed out after ${this.options.timeoutMs}ms.`);
      }

      throw error;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async fetchDashboardPayload() {
    return this.withFallback(
      "fetchDashboardPayload",
      () => this.request<DashboardPayload>(SPRING_API_ENDPOINTS.dashboard),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchDashboardPayload() : undefined,
    );
  }

  async fetchOperationsPayload() {
    return this.withFallback(
      "fetchOperationsPayload",
      () => this.request<OperationsPayload>(SPRING_API_ENDPOINTS.operations),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchOperationsPayload() : undefined,
    );
  }

  async createBatch(input: CreateBatchInput) {
    return this.withFallback(
      "createBatch",
      () =>
        this.request<ProductionBatch>(SPRING_API_ENDPOINTS.operationBatches, {
          method: "POST",
          body: JSON.stringify(input),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.createBatch(input) : undefined,
    );
  }

  async createInventoryLog(input: CreateInventoryInput) {
    return this.withFallback(
      "createInventoryLog",
      () =>
        this.request<InventoryLog>(SPRING_API_ENDPOINTS.operationInventoryLogs, {
          method: "POST",
          body: JSON.stringify(input),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.createInventoryLog(input) : undefined,
    );
  }

  async createWasteLog(input: CreateWasteInput) {
    return this.withFallback(
      "createWasteLog",
      () =>
        this.request<WasteLog>(SPRING_API_ENDPOINTS.operationWasteLogs, {
          method: "POST",
          body: JSON.stringify(input),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.createWasteLog(input) : undefined,
    );
  }

  async fetchMaterials() {
    return this.withFallback(
      "fetchMaterials",
      () => this.request<Material[]>(SPRING_API_ENDPOINTS.materials),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchMaterials() : undefined,
    );
  }

  async upsertMaterial(input: Material) {
    return this.withFallback(
      "upsertMaterial",
      async () => {
        if (input.id) {
          await this.request<Material>(`${SPRING_API_ENDPOINTS.materials}/${input.id}`, {
            method: "PUT",
            body: JSON.stringify(input),
          });
          return this.request<Material[]>(SPRING_API_ENDPOINTS.materials);
        }

        await this.request<Material>(SPRING_API_ENDPOINTS.materials, {
          method: "POST",
          body: JSON.stringify(input),
        });
        return this.request<Material[]>(SPRING_API_ENDPOINTS.materials);
      },
      this.options.fallbackApi ? () => this.options.fallbackApi!.upsertMaterial(input) : undefined,
    );
  }

  async fetchTemplates() {
    return this.withFallback(
      "fetchTemplates",
      () => this.request<ProductionTemplate[]>(SPRING_API_ENDPOINTS.templates),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchTemplates() : undefined,
    );
  }

  async upsertTemplate(input: ProductionTemplate) {
    return this.withFallback(
      "upsertTemplate",
      async () => {
        if (input.id) {
          await this.request<ProductionTemplate>(`${SPRING_API_ENDPOINTS.templates}/${input.id}`, {
            method: "PUT",
            body: JSON.stringify(input),
          });
          return this.request<ProductionTemplate[]>(SPRING_API_ENDPOINTS.templates);
        }

        await this.request<ProductionTemplate>(SPRING_API_ENDPOINTS.templates, {
          method: "POST",
          body: JSON.stringify(input),
        });
        return this.request<ProductionTemplate[]>(SPRING_API_ENDPOINTS.templates);
      },
      this.options.fallbackApi ? () => this.options.fallbackApi!.upsertTemplate(input) : undefined,
    );
  }

  async fetchInsights() {
    return this.withFallback(
      "fetchInsights",
      () => this.request<InsightsPayload>(SPRING_API_ENDPOINTS.insights),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchInsights() : undefined,
    );
  }

  async updateInsightStatus(id: string, status: CircularInsight["status"]) {
    return this.withFallback(
      "updateInsightStatus",
      () =>
        this.request<InsightsPayload>(`${SPRING_API_ENDPOINTS.insights}/${id}/status`, {
          method: "PATCH",
          body: JSON.stringify({ status }),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.updateInsightStatus(id, status) : undefined,
    );
  }

  async updateAnomalyStatus(id: string, status: CircularInsight["status"]) {
    return this.withFallback(
      "updateAnomalyStatus",
      () =>
        this.request<InsightsPayload["anomalies"]>(`${SPRING_API_ENDPOINTS.anomalies}/${id}/status`, {
          method: "PATCH",
          body: JSON.stringify({ status }),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.updateAnomalyStatus(id, status) : undefined,
    );
  }

  async fetchAnalytics() {
    return this.withFallback(
      "fetchAnalytics",
      () => this.request<AnalyticsPayload>(SPRING_API_ENDPOINTS.analytics),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchAnalytics() : undefined,
    );
  }

  async processOcrImage(file?: File) {
    if (!file) {
      throw new Error("Please upload an invoice image first.");
    }

    const body = new FormData();
    body.append("image", file);

    return this.withFallback(
      "processOcrImage",
      () =>
        this.request<OcrMaterialLine[]>(SPRING_API_ENDPOINTS.ocr, {
          method: "POST",
          body,
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.processOcrImage(file) : undefined,
    );
  }

  async fetchSettings() {
    return this.withFallback(
      "fetchSettings",
      () => this.request<UserSettings>(SPRING_API_ENDPOINTS.settings),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchSettings() : undefined,
    );
  }

  async saveSettings(input: UserSettings) {
    return this.withFallback(
      "saveSettings",
      () =>
        this.request<UserSettings>(SPRING_API_ENDPOINTS.settings, {
          method: "PUT",
          body: JSON.stringify(input),
        }),
      this.options.fallbackApi ? () => this.options.fallbackApi!.saveSettings(input) : undefined,
    );
  }
}

const provider = (import.meta.env.VITE_INTERNAL_API_PROVIDER as string | undefined)?.toLowerCase() ?? "mock";
const springBaseUrl = (import.meta.env.VITE_SPRING_API_BASE_URL as string | undefined) ?? "http://localhost:8080";
const springTimeoutMs = parseTimeoutMs(
  import.meta.env.VITE_SPRING_API_TIMEOUT_MS as string | undefined,
  DEFAULT_SPRING_TIMEOUT_MS,
);
const springFallbackToMock = parseBooleanEnv(
  import.meta.env.VITE_SPRING_FALLBACK_TO_MOCK as string | undefined,
  true,
);

const mockApi = new MockInternalApi();

export const internalApiRuntimeConfig = {
  provider,
  springBaseUrl,
  springTimeoutMs,
  springFallbackToMock,
} as const;

export const internalApi: InternalApi = provider === "spring"
  ? new SpringBootInternalApi(springBaseUrl, {
      timeoutMs: springTimeoutMs,
      fallbackApi: springFallbackToMock ? mockApi : undefined,
    })
  : mockApi;

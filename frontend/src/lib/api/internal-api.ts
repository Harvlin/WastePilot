import {
  ActivityLogEntry,
  AnalyticsPayload,
  AuditTrailEntry,
  BatchCloseSummary,
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
import * as mock from "@/features/internal/mock-api";
import {
  AuthSession,
  AuthUser,
  clearStoredAuthSession,
  getStoredAuthSession,
} from "@/features/auth/auth-storage";
import { signInMockUser, signOutMockUser } from "@/lib/mock-auth";

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
export interface CloseBatchInput {
  batchId: string;
  outputUnits: number;
  closeReason?: string;
}

export interface RecoverWasteInput {
  wasteLogId: string;
}

export interface WasteRecoveryResult {
  wasteLog: WasteLog;
  inventoryLog: InventoryLog;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface SignupInput {
  fullName: string;
  email: string;
  password: string;
}

type RequestErrorKind = "http" | "network" | "timeout";

class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly kind: RequestErrorKind,
    readonly status?: number,
  ) {
    super(message);
  }
}

export interface InternalApiStatus {
  fallbackActive: boolean;
  fallbackOperations: string[];
}

const DEFAULT_SPRING_TIMEOUT_MS = 10_000;

export const SPRING_API_ENDPOINTS = {
  dashboard: "/api/v1/dashboard",
  operations: "/api/v1/operations",
  operationBatches: "/api/v1/operations/batches",
  operationInventoryLogs: "/api/v1/operations/inventory-logs",
  operationWasteLogs: "/api/v1/operations/waste-logs",
  operationWasteRecover: "/api/v1/operations/waste-logs/recover",
  operationBatchCloseSummary: "/api/v1/operations/batch-close/summary",
  operationBatchClose: "/api/v1/operations/batch-close",
  materials: "/api/v1/materials",
  templates: "/api/v1/templates",
  insights: "/api/v1/ai/insights",
  anomalies: "/api/v1/ai/anomaly",
  analytics: "/api/v1/analytics",
  reports: "/api/v1/reports",
  ocr: "/api/v1/ai/ocr",
  settings: "/api/v1/settings",
  activityLogs: "/api/v1/integrity/activity-logs",
  auditTrail: "/api/v1/integrity/audit-trail",
  integrityOverview: "/api/v1/integrity/overview",
  authLogin: "/api/v1/auth/login",
  authSignup: "/api/v1/auth/signup",
  authMe: "/api/v1/auth/me",
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
  login(input: LoginInput): Promise<AuthSession>;
  signup(input: SignupInput): Promise<AuthSession>;
  fetchCurrentUser(): Promise<AuthUser>;
  logout(): Promise<void>;
  fetchDashboardPayload(): Promise<DashboardPayload>;
  fetchOperationsPayload(): Promise<OperationsPayload>;
  createBatch(input: CreateBatchInput): Promise<ProductionBatch>;
  createInventoryLog(input: CreateInventoryInput): Promise<InventoryLog>;
  createWasteLog(input: CreateWasteInput): Promise<WasteLog>;
  recoverWasteToInventory(input: RecoverWasteInput): Promise<WasteRecoveryResult>;
  fetchBatchCloseSummary(batchId: string): Promise<BatchCloseSummary>;
  closeBatch(input: CloseBatchInput): Promise<BatchCloseSummary>;
  fetchActivityLogs(batchId?: string): Promise<ActivityLogEntry[]>;
  fetchAuditTrail(batchId?: string): Promise<AuditTrailEntry[]>;
  fetchIntegrityOverview(): Promise<IntegrityOverview>;
  fetchMaterials(): Promise<Material[]>;
  upsertMaterial(input: Material): Promise<Material[]>;
  deleteMaterial(id: string): Promise<Material[]>;
  fetchTemplates(): Promise<ProductionTemplate[]>;
  upsertTemplate(input: ProductionTemplate): Promise<ProductionTemplate[]>;
  deleteTemplate(id: string): Promise<ProductionTemplate[]>;
  fetchInsights(): Promise<InsightsPayload>;
  updateInsightStatus(id: string, status: CircularInsight["status"]): Promise<InsightsPayload>;
  updateAnomalyStatus(id: string, status: CircularInsight["status"]): Promise<InsightsPayload["anomalies"]>;
  fetchAnalytics(): Promise<AnalyticsPayload>;
  fetchReportsPayload(period: ReportPeriod): Promise<ReportsPayload>;
  processOcrImage(file?: File): Promise<OcrMaterialLine[]>;
  fetchSettings(): Promise<UserSettings>;
  saveSettings(input: UserSettings): Promise<UserSettings>;
}

class MockInternalApi implements InternalApi {
  async login(input: LoginInput): Promise<AuthSession> {
    signInMockUser(input.email, input.email.split("@")[0]);
    return {
      accessToken: "mock-token",
      tokenType: "Bearer",
      user: {
        id: "mock-user",
        fullName: input.email.split("@")[0],
        email: input.email,
      },
    };
  }

  async signup(input: SignupInput): Promise<AuthSession> {
    signInMockUser(input.email, input.fullName);
    return {
      accessToken: "mock-token",
      tokenType: "Bearer",
      user: {
        id: "mock-user",
        fullName: input.fullName,
        email: input.email,
      },
    };
  }

  async fetchCurrentUser(): Promise<AuthUser> {
    const session = getStoredAuthSession();
    if (!session?.user) {
      throw new Error("No active session.");
    }
    return session.user;
  }

  async logout(): Promise<void> {
    signOutMockUser();
  }

  fetchDashboardPayload = mock.fetchDashboardPayload;
  fetchOperationsPayload = mock.fetchOperationsPayload;
  createBatch = mock.createBatch;
  createInventoryLog = mock.createInventoryLog;
  createWasteLog = mock.createWasteLog;
  recoverWasteToInventory = mock.recoverWasteToInventory;
  fetchBatchCloseSummary = mock.fetchBatchCloseSummary;
  closeBatch = mock.closeBatch;
  fetchActivityLogs = mock.fetchActivityLogs;
  fetchAuditTrail = mock.fetchAuditTrail;
  fetchIntegrityOverview = mock.fetchIntegrityOverview;
  fetchMaterials = mock.fetchMaterials;
  upsertMaterial = mock.upsertMaterial;
  deleteMaterial = mock.deleteMaterial;
  fetchTemplates = mock.fetchTemplates;
  upsertTemplate = mock.upsertTemplate;
  deleteTemplate = mock.deleteTemplate;
  fetchInsights = mock.fetchInsights;
  updateInsightStatus = mock.updateInsightStatus;
  updateAnomalyStatus = mock.updateAnomalyStatus;
  fetchAnalytics = mock.fetchAnalytics;
  fetchReportsPayload = mock.fetchReportsPayload;
  processOcrImage = mock.processOcrImage;
  fetchSettings = mock.fetchSettings;
  saveSettings = mock.saveSettings;
}

interface SpringApiOptions {
  timeoutMs: number;
  fallbackApi?: InternalApi;
}

const fallbackOperations = new Set<string>();
const statusListeners = new Set<(status: InternalApiStatus) => void>();

function emitInternalApiStatus() {
  const status: InternalApiStatus = {
    fallbackActive: fallbackOperations.size > 0,
    fallbackOperations: Array.from(fallbackOperations).sort(),
  };
  statusListeners.forEach((listener) => listener(status));
}

export function subscribeInternalApiStatus(listener: (status: InternalApiStatus) => void) {
  statusListeners.add(listener);
  listener({
    fallbackActive: fallbackOperations.size > 0,
    fallbackOperations: Array.from(fallbackOperations).sort(),
  });
  return () => statusListeners.delete(listener);
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
      const value = await primaryCall();
      if (fallbackOperations.delete(operation)) {
        emitInternalApiStatus();
      }
      return value;
    } catch (error) {
      if (!fallbackCall) {
        throw error;
      }

      if (!this.shouldFallback(error)) {
        throw error;
      }

      this.logFallback(operation, error);
      const value = await fallbackCall();
      fallbackOperations.add(operation);
      emitInternalApiStatus();
      return value;
    }
  }

  private shouldFallback(error: unknown) {
    if (!(error instanceof ApiRequestError)) {
      return false;
    }
    if (error.kind === "network" || error.kind === "timeout") {
      return true;
    }
    return typeof error.status === "number" && error.status >= 500;
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.options.timeoutMs);

    try {
      const isFormData = init?.body instanceof FormData;
      const authSession = getStoredAuthSession();
      const authHeader = authSession?.accessToken
        ? { Authorization: `Bearer ${authSession.accessToken}` }
        : {};
      const response = await fetch(`${this.baseUrl}${path}`, {
        credentials: "include",
        headers: {
          ...(isFormData ? {} : { "Content-Type": "application/json" }),
          ...authHeader,
          ...(init?.headers ?? {}),
        },
        ...init,
        signal: controller.signal,
      });

      if (!response.ok) {
        const fallback = `Request failed with status ${response.status}`;
        const text = await response.text();
        let message = text || fallback;
        try {
          const parsed = JSON.parse(text) as { message?: string };
          if (parsed?.message) {
            message = parsed.message;
          }
        } catch {
          // keep original message
        }
        if (response.status === 401 || response.status === 403) {
          clearStoredAuthSession();
        }
        throw new ApiRequestError(message, "http", response.status);
      }

      if (response.status === 204) {
        return undefined as T;
      }

      const contentType = response.headers.get("content-type") ?? "";
      if (!contentType.includes("application/json")) {
        const text = await response.text();
        throw new ApiRequestError(text || `Expected JSON response from ${path}.`, "http", response.status);
      }

      const text = await response.text();
      if (!text.trim()) {
        return undefined as T;
      }

      try {
        return JSON.parse(text) as T;
      } catch {
        throw new ApiRequestError(`Invalid JSON response from ${path}.`, "http", response.status);
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw new ApiRequestError(`Request timed out after ${this.options.timeoutMs}ms.`, "timeout");
      }
      if (error instanceof TypeError) {
        throw new ApiRequestError("Network error while contacting backend service.", "network");
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

  async login(input: LoginInput) {
    return this.request<AuthSession>(SPRING_API_ENDPOINTS.authLogin, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async signup(input: SignupInput) {
    return this.request<AuthSession>(SPRING_API_ENDPOINTS.authSignup, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchCurrentUser() {
    return this.request<AuthUser>(SPRING_API_ENDPOINTS.authMe);
  }

  async logout() {
    return Promise.resolve();
  }

  async fetchOperationsPayload() {
    return this.withFallback(
      "fetchOperationsPayload",
      () => this.request<OperationsPayload>(SPRING_API_ENDPOINTS.operations),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchOperationsPayload() : undefined,
    );
  }

  async createBatch(input: CreateBatchInput) {
    return this.request<ProductionBatch>(SPRING_API_ENDPOINTS.operationBatches, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async createInventoryLog(input: CreateInventoryInput) {
    return this.request<InventoryLog>(SPRING_API_ENDPOINTS.operationInventoryLogs, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async createWasteLog(input: CreateWasteInput) {
    return this.request<WasteLog>(SPRING_API_ENDPOINTS.operationWasteLogs, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async recoverWasteToInventory(input: RecoverWasteInput) {
    return this.request<WasteRecoveryResult>(SPRING_API_ENDPOINTS.operationWasteRecover, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchBatchCloseSummary(batchId: string) {
    return this.withFallback(
      "fetchBatchCloseSummary",
      () => this.request<BatchCloseSummary>(`${SPRING_API_ENDPOINTS.operationBatchCloseSummary}/${batchId}`),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchBatchCloseSummary(batchId) : undefined,
    );
  }

  async closeBatch(input: CloseBatchInput) {
    return this.request<BatchCloseSummary>(SPRING_API_ENDPOINTS.operationBatchClose, {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async fetchActivityLogs(batchId?: string) {
    return this.withFallback(
      "fetchActivityLogs",
      () => {
        const query = batchId ? `?batchId=${encodeURIComponent(batchId)}` : "";
        return this.request<ActivityLogEntry[]>(`${SPRING_API_ENDPOINTS.activityLogs}${query}`);
      },
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchActivityLogs(batchId) : undefined,
    );
  }

  async fetchAuditTrail(batchId?: string) {
    return this.withFallback(
      "fetchAuditTrail",
      () => {
        const query = batchId ? `?batchId=${encodeURIComponent(batchId)}` : "";
        return this.request<AuditTrailEntry[]>(`${SPRING_API_ENDPOINTS.auditTrail}${query}`);
      },
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchAuditTrail(batchId) : undefined,
    );
  }

  async fetchIntegrityOverview() {
    return this.withFallback(
      "fetchIntegrityOverview",
      () => this.request<IntegrityOverview>(SPRING_API_ENDPOINTS.integrityOverview),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchIntegrityOverview() : undefined,
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
  }

  async deleteMaterial(id: string) {
    await this.request<void>(`${SPRING_API_ENDPOINTS.materials}/${id}`, {
      method: "DELETE",
    });
    return this.request<Material[]>(SPRING_API_ENDPOINTS.materials);
  }

  async fetchTemplates() {
    return this.withFallback(
      "fetchTemplates",
      () => this.request<ProductionTemplate[]>(SPRING_API_ENDPOINTS.templates),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchTemplates() : undefined,
    );
  }

  async upsertTemplate(input: ProductionTemplate) {
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
  }

  async deleteTemplate(id: string) {
    await this.request<void>(`${SPRING_API_ENDPOINTS.templates}/${id}`, {
      method: "DELETE",
    });
    return this.request<ProductionTemplate[]>(SPRING_API_ENDPOINTS.templates);
  }

  async fetchInsights() {
    return this.withFallback(
      "fetchInsights",
      () => this.request<InsightsPayload>(SPRING_API_ENDPOINTS.insights),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchInsights() : undefined,
    );
  }

  async updateInsightStatus(id: string, status: CircularInsight["status"]) {
    return this.request<InsightsPayload>(`${SPRING_API_ENDPOINTS.insights}/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  }

  async updateAnomalyStatus(id: string, status: CircularInsight["status"]) {
    return this.request<InsightsPayload["anomalies"]>(`${SPRING_API_ENDPOINTS.anomalies}/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  }

  async fetchAnalytics() {
    return this.withFallback(
      "fetchAnalytics",
      () => this.request<AnalyticsPayload>(SPRING_API_ENDPOINTS.analytics),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchAnalytics() : undefined,
    );
  }

  async fetchReportsPayload(period: ReportPeriod) {
    return this.withFallback(
      "fetchReportsPayload",
      () => this.request<ReportsPayload>(`${SPRING_API_ENDPOINTS.reports}?period=${encodeURIComponent(period)}`),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchReportsPayload(period) : undefined,
    );
  }

  async processOcrImage(file?: File) {
    if (!file) {
      throw new Error("Please upload an invoice image first.");
    }

    const body = new FormData();
    body.append("image", file);

    return this.request<OcrMaterialLine[]>(SPRING_API_ENDPOINTS.ocr, {
      method: "POST",
      body,
    });
  }

  async fetchSettings() {
    return this.withFallback(
      "fetchSettings",
      () => this.request<UserSettings>(SPRING_API_ENDPOINTS.settings),
      this.options.fallbackApi ? () => this.options.fallbackApi!.fetchSettings() : undefined,
    );
  }

  async saveSettings(input: UserSettings) {
    return this.request<UserSettings>(SPRING_API_ENDPOINTS.settings, {
      method: "PUT",
      body: JSON.stringify(input),
    });
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

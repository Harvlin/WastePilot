import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, internalApi } from "@/lib/api/internal-api";
import {
  AUTH_SESSION_STORAGE_KEY,
  AuthSession,
} from "@/features/auth/auth-storage";

const session: AuthSession = {
  accessToken: "valid-token",
  tokenType: "Bearer",
  user: { id: "user-1", fullName: "Operator", email: "operator@example.com", role: "OPERATOR" },
};

describe("internal API error handling", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("defaults to the real backend when the provider environment variable is unset", async () => {
    vi.stubEnv("VITE_INTERNAL_API_PROVIDER", "");
    vi.resetModules();
    try {
      const { internalApiRuntimeConfig } = await import("@/lib/api/internal-api");
      expect(internalApiRuntimeConfig.provider).toBe("spring");
      expect(internalApiRuntimeConfig.configurationError).toBeUndefined();
    } finally {
      vi.unstubAllEnvs();
      vi.resetModules();
    }
  });

  it("preserves structured backend error codes", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(
      JSON.stringify({ code: "CLOSE_REASON_REQUIRED", message: "A reason is required." }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    ));

    await expect(internalApi.fetchDashboardPayload()).rejects.toMatchObject({
      status: 400,
      code: "CLOSE_REASON_REQUIRED",
      message: "A reason is required.",
    });
  });

  it("clears the session on 401", async () => {
    localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
    vi.mocked(fetch).mockResolvedValue(new Response(
      JSON.stringify({ code: "UNAUTHORIZED", message: "Session expired." }),
      { status: 401, headers: { "Content-Type": "application/json" } },
    ));

    await expect(internalApi.fetchDashboardPayload()).rejects.toBeInstanceOf(ApiRequestError);
    expect(localStorage.getItem(AUTH_SESSION_STORAGE_KEY)).toBeNull();
  });

  it("preserves the session and returns a permission-specific error on 403", async () => {
    localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
    vi.mocked(fetch).mockResolvedValue(new Response(
      JSON.stringify({ code: "SUPERVISOR_REQUIRED", message: "Supervisor role required." }),
      { status: 403, headers: { "Content-Type": "application/json" } },
    ));

    await expect(internalApi.fetchDashboardPayload()).rejects.toMatchObject({
      status: 403,
      code: "SUPERVISOR_REQUIRED",
      message: "Supervisor role required.",
    });
    expect(localStorage.getItem(AUTH_SESSION_STORAGE_KEY)).not.toBeNull();
  });
});

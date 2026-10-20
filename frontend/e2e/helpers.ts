import { expect, type Page } from "@playwright/test";

export async function authenticateAs(page: Page, role: "OPERATOR" | "SUPERVISOR") {
  const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://127.0.0.1:8080";
  const email = role === "SUPERVISOR"
    ? "e2e-supervisor@wastepilot.local"
    : `e2e-operator-${Date.now()}-${Math.random().toString(16).slice(2)}@wastepilot.local`;
  const password = "SecurePass123!";

  if (role === "OPERATOR") {
    const signup = await page.request.post(`${apiBaseUrl}/api/v1/auth/signup`, {
      data: { fullName: "E2E Operator", email, password },
    });
    expect(signup.status()).toBe(201);
  }

  const login = await page.request.post(`${apiBaseUrl}/api/v1/auth/login`, {
    data: { email, password },
  });
  expect(login.ok()).toBeTruthy();
  const session = await login.json();

  await page.addInitScript((authSession) => {
    window.localStorage.setItem("wastepilot_auth_session", JSON.stringify(authSession));
  }, session);
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
}

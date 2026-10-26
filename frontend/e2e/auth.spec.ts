import { test, expect } from "@playwright/test";

test("user can sign up, sign out, and sign in", async ({ page }) => {
  const testEmail = `tester-${Date.now()}@wastepilot.local`;

  await page.goto("/auth");
  await expect(page).toHaveTitle(/WastePilot/);

  await page.getByRole("button", { name: "Sign up" }).click();
  await page.locator("#signup-name").fill("Playwright Tester");
  await page.locator("#auth-email").fill(testEmail);
  await page.locator("#auth-password").fill("SecurePass123!");
  await page.getByRole("button", { name: "Create Account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL("/");

  await page.goto("/auth");
  await page.locator("#auth-email").fill(testEmail);
  await page.locator("#auth-password").fill("SecurePass123!");
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
});

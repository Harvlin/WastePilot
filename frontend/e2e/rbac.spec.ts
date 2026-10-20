import { test, expect } from "@playwright/test";
import { authenticateAs } from "./helpers";

test("operator does not see supervisor-only pattern review", async ({ page }) => {
  await authenticateAs(page, "OPERATOR");
  await page.goto("/operations");
  await page.getByRole("button", { name: /^5\. Integrity Check/ }).click();

  await expect(page.getByRole("heading", { name: "Activity Logs" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Pattern Review" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Audit Chain Verification" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Cross-validation" })).toHaveCount(0);
});

test("supervisor can see pattern review", async ({ page }) => {
  await authenticateAs(page, "SUPERVISOR");
  await page.goto("/operations");
  await page.getByRole("button", { name: /^5\. Integrity Check/ }).click();

  await expect(page.getByRole("heading", { name: "Activity Logs" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Audit Chain Verification" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Cross-validation" })).toBeVisible();
});

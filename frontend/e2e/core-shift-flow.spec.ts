import { test, expect } from "@playwright/test";
import { authenticateAs } from "./helpers";

test("operator completes the full shift workflow", async ({ page }) => {
  await authenticateAs(page, "OPERATOR");
  await page.goto("/operations");

  await page.getByRole("button", { name: "Start Batch", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Inventory Input" })).toBeVisible();

  await page.getByRole("button", { name: "Add Log" }).click();
  await expect(page.getByRole("heading", { name: "Waste Classification" })).toBeVisible();

  await page.getByRole("button", { name: "Log Waste", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Batch Close Assistant" })).toBeVisible();

  const closeReason = page.locator('input[placeholder*="Reason"]');
  if (await closeReason.isVisible()) {
    await closeReason.fill("Verified final shift totals.");
  }

  await page.getByRole("button", { name: "Confirm Batch Close" }).click();
  await page.getByRole("button", { name: "Yes, close batch" }).click();

  await expect(page.getByRole("heading", { name: "Activity Logs" })).toBeVisible();
  await expect(page.getByText("Batch closed successfully with integrity snapshot.")).toBeVisible();
});

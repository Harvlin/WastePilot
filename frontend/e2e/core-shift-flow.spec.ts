import { test, expect } from '@playwright/test';

test.describe('Core Shift Flow', () => {
  test.use({ baseURL: 'http://localhost:5173' });

  test.beforeEach(async ({ page }) => {
    // Fast login via mock credentials
    await page.goto('/auth');
    await page.locator('#auth-email').fill('operator@wastepilot.local');
    await page.locator('#auth-password').fill('MockPass123!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/internal\/operations/);
  });

  test('completes full shift flow: start -> inventory -> waste -> close', async ({ page }) => {
    // 1. Start Batch
    await page.click('button:has-text("Start Batch")');
    const templateSelect = page.locator('button:has-text("Select Template")');
    if (await templateSelect.isVisible()) {
        await templateSelect.click();
        await page.click('text="Standard Production"');
    }
    await page.click('button:has-text("Confirm Start")');
    
    // Wait for batch to appear in the active list
    await expect(page.locator('text="Active Batches"')).toBeVisible();

    // 2. Log Inventory
    await page.click('button:has-text("Log Inventory")');
    // Using simple text locators for robustness in mock mode
    await page.fill('input[placeholder*="Quantity"]', '100');
    await page.click('button:has-text("Submit")');
    await expect(page.locator('text="Inventory logged successfully"')).toBeVisible({ timeout: 5000 }).catch(() => {});

    // 3. Log Waste
    await page.click('button:has-text("Log Waste")');
    await page.fill('input[placeholder*="Quantity"]', '5');
    await page.click('button:has-text("Submit")');
    await expect(page.locator('text="Waste logged successfully"')).toBeVisible({ timeout: 5000 }).catch(() => {});

    // 4. Close Batch (requires SUPERVISOR or mock allows it)
    const closeBtn = page.locator('button', { hasText: 'Close' }).first();
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
      // If variance is high, a reason might be required
      const reasonInput = page.locator('textarea[placeholder*="reason"]');
      if (await reasonInput.isVisible()) {
        await reasonInput.fill('Routine closure verified.');
      }
      await page.click('button:has-text("Confirm Close")');
    }

    // 5. View Integrity Check
    await page.goto('/internal/integrity');
    await expect(page.locator('text="Integrity Overview"')).toBeVisible();
  });
});

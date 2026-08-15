import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  // Use mock provider in E2E tests for stability without a live backend
  test.use({ 
    baseURL: 'http://localhost:5173',
  });

  test('signup, login, and logout successfully', async ({ page }) => {
    // 1. Navigate to auth page
    await page.goto('/auth');
    await expect(page).toHaveTitle(/WastePilot/);

    // 2. Switch to Sign Up
    const toggleButton = page.locator('button', { hasText: /Sign Up|Create an account/i }).first();
    // In our UI, it might be a tab or button "Sign Up"
    if (await toggleButton.isVisible()) {
      await toggleButton.click();
    }

    // 3. Fill signup form
    const nameInput = page.locator('#signup-name');
    if (await nameInput.isVisible()) {
      await nameInput.fill('Playwright Tester');
    }
    
    await page.locator('#auth-email').fill('tester@wastepilot.local');
    await page.locator('#auth-password').fill('SecurePass123!');
    
    // 4. Submit
    await page.locator('button[type="submit"]').click();

    // 5. Verify redirection to internal operations page
    await expect(page).toHaveURL(/\/internal\/operations/);
    await expect(page.locator('h1', { hasText: /Live Factory Floor/i })).toBeVisible();

    // 6. Logout
    const logoutBtn = page.locator('button', { hasText: /Log out/i });
    if (await logoutBtn.isVisible()) {
      await logoutBtn.click();
      await expect(page).toHaveURL(/(\/|\/auth)/);
    }
  });
});

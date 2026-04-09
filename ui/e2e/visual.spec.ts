import { expect, test } from '@playwright/test';

test('login page screenshot', async ({ page }) => {
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  await expect(page).toHaveScreenshot('login.png', {
    fullPage: true,
    animations: 'disabled',
  });
});

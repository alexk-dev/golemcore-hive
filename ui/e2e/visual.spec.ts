import { expect, test } from '@playwright/test';

test('login page screenshot', async ({ page }, testInfo) => {
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  await page.locator('button[type="submit"]').waitFor();
  await testInfo.attach('login.png', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
});

test('mobile login page has no horizontal overflow', async ({ page }, testInfo) => {
  if (testInfo.project.name !== 'mobile') {
    test.skip();
  }
  await page.goto('/login');
  const body = page.locator('body');
  const box = await body.boundingBox();
  const viewport = page.viewportSize();
  expect(box?.width).toBeLessThanOrEqual(viewport?.width ?? 0);
});

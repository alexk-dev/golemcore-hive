import { expect, test, type Page } from '@playwright/test';

const ROUTES = [
  { path: '/', name: 'home' },
  { path: '/boards', name: 'boards' },
  { path: '/policies', name: 'policies' },
  { path: '/approvals', name: 'approvals' },
  { path: '/fleet', name: 'golems' },
  { path: '/fleet/chat', name: 'chat' },
  { path: '/fleet/roles', name: 'roles' },
  { path: '/audit', name: 'audit' },
  { path: '/budgets', name: 'budgets' },
  { path: '/settings', name: 'settings' },
];

async function login(page: Page) {
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  // Username input has placeholder="admin" and autoComplete="username"
  const usernameInput = page.locator('input[autocomplete="username"]');
  await usernameInput.waitFor({ timeout: 10000 });
  await usernameInput.fill('admin');
  await page.locator('input[type="password"]').fill('change-me-now');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL('**/', { timeout: 10000 });
}

async function checkNoOverflow(page: Page, route: string) {
  const viewport = page.viewportSize();
  if (!viewport) return;
  const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
  expect(
    bodyWidth,
    `Horizontal overflow on ${route}: scrollWidth ${bodyWidth} > viewport ${viewport.width}`,
  ).toBeLessThanOrEqual(viewport.width + 2);
}

test.describe('walkthrough all sections', () => {
  test.setTimeout(90000);

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('domcontentloaded');
    await page.locator('button[type="submit"]').waitFor();

    await expect(page.locator('button[type="submit"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();

    await checkNoOverflow(page, '/login');
    await expect(page).toHaveScreenshot('walkthrough-login.png', {
      fullPage: true,
      animations: 'disabled',
    });
  });

  test('navigate all authenticated sections', async ({ page }) => {
    await login(page);

    for (const route of ROUTES) {
      // Use click-based navigation instead of page.goto to avoid full page reload
      // which loses React state and causes race conditions
      const navLink = page.locator(`aside a[href="${route.path}"]`).first();
      if (await navLink.isVisible().catch(() => false)) {
        await navLink.click();
      } else {
        await page.goto(route.path, { waitUntil: 'load' });
      }
      await page.waitForURL(`**${route.path}`, { timeout: 8000 }).catch(() => {});
      await page.waitForTimeout(1500);

      // Page should not be blank
      const rootContent = await page.locator('#root').innerHTML();
      expect(rootContent.length, `${route.name} page at ${route.path} appears blank`).toBeGreaterThan(100);

      // No horizontal overflow
      await checkNoOverflow(page, `${route.path} (${route.name})`);

      // Screenshot
      await expect(page).toHaveScreenshot(`walkthrough-${route.name}.png`, {
        fullPage: true,
        animations: 'disabled',
      });
    }
  });

  test('mobile sidebar opens and closes', async ({ page }, testInfo) => {
    if (testInfo.project.name !== 'mobile') {
      test.skip();
    }

    await login(page);

    const hamburger = page.locator('button[aria-label="Open menu"]');
    await expect(hamburger).toBeVisible();

    await hamburger.click();
    await page.waitForTimeout(300);

    // Mobile sidebar should be visible (translate-x-0)
    const mobileSidebar = page.locator('aside.translate-x-0');
    await expect(mobileSidebar).toBeVisible();

    await expect(page).toHaveScreenshot('walkthrough-mobile-sidebar-open.png', {
      fullPage: true,
      animations: 'disabled',
    });

    // Click a nav item — sidebar should close
    await page.locator('aside.translate-x-0').locator('a', { hasText: 'Boards' }).click();
    await page.waitForTimeout(400);
    await expect(page.locator('aside.translate-x-0')).not.toBeVisible();
  });

  test('desktop sidebar always visible, no hamburger', async ({ page }, testInfo) => {
    if (testInfo.project.name !== 'desktop') {
      test.skip();
    }

    await login(page);
    await expect(page.locator('aside').first()).toBeVisible();
    await expect(page.locator('button[aria-label="Open menu"]')).not.toBeVisible();
  });

  test('tables adapt on mobile without overflow', async ({ page }, testInfo) => {
    if (testInfo.project.name !== 'mobile') {
      test.skip();
    }

    await login(page);

    for (const path of ['/approvals', '/budgets', '/audit']) {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(800);
      await checkNoOverflow(page, `${path} (mobile)`);
    }
  });
});

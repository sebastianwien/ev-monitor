import { test, expect, devices } from '@playwright/test';

// iPhone 12 viewport direkt pro Test setzen (test.use in describe geht nicht mit defaultBrowserType)
const iphone12 = devices['iPhone 12'];

test('Mobile: Landing Page vollständig nutzbar', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/');

  const h1 = page.locator('h1').first();
  await expect(h1).toBeVisible();

  const cta = page.locator('a[href*="/modelle"]').first();
  await expect(cta).toBeVisible();
  const box = await cta.boundingBox();
  expect(box).not.toBeNull();
  // CTA darf nicht ausserhalb des Viewports rechts abgeschnitten sein (iPhone 12: 390px)
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);
  await context.close();
});

test('Mobile: /modelle zeigt Modell-Cards ohne Layout-Bruch', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/modelle');

  const firstCard = page.locator('a[href*="/modelle/"]').first();
  await expect(firstCard).toBeVisible({ timeout: 10_000 });

  const box = await firstCard.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);
  await context.close();
});

test('Mobile: Login-Formular bedienbar', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/login');

  await expect(page.locator('input[type="text"]')).toBeVisible();
  await expect(page.locator('input[type="password"]')).toBeVisible();
  await expect(page.locator('button[type="submit"]')).toBeVisible();

  await page.locator('input[type="text"]').tap();
  await page.locator('input[type="text"]').fill('test@test.de');
  await expect(page.locator('input[type="text"]')).toHaveValue('test@test.de');
  await context.close();
});

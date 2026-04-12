import { test, expect } from '@playwright/test';
import { TEST_USER } from './global-setup';

async function login(page: any) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('rendert ohne JS-Fehler', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1:has-text("Dashboard")')).toBeVisible({ timeout: 10_000 });

    expect(errors).toEqual([]);
  });

  test('Fahrzeuge-Button sichtbar', async ({ page }) => {
    await page.waitForLoadState('networkidle');
    await expect(page.locator('a[href="/cars"]')).toBeVisible({ timeout: 10_000 });
  });

  test('Zeitraum-Filter ohne Crash', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    const timeSelect = page.locator('select').first();
    if (await timeSelect.isVisible()) {
      await timeSelect.selectOption('ALL_TIME');
      await page.waitForTimeout(1000);
    }

    expect(errors).toEqual([]);
  });
});

test.describe('Fahrzeugverwaltung', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('/cars rendert ohne JS-Fehler', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/cars');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1')).toBeVisible({ timeout: 10_000 });

    expect(errors).toEqual([]);
  });

  test('/cars zeigt Fahrzeug-Inhalt', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/cars');
    await page.waitForLoadState('networkidle');

    const pageContent = await page.textContent('body');
    const hasCarContent = pageContent?.includes('hinzufügen') || pageContent?.includes('Add') || pageContent?.includes('Fahrzeug');
    expect(hasCarContent).toBe(true);

    expect(errors).toEqual([]);
  });
});

test.describe('Einstellungen', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('/settings rendert ohne JS-Fehler', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/settings');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator(`text=${TEST_USER.email}`)).toBeVisible({ timeout: 5_000 });

    expect(errors).toEqual([]);
  });

  test('Ladekarten-Sektion rendert', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/settings');
    await page.waitForLoadState('networkidle');

    const providerSection = page.locator('text=/Ladekarte|Charging Card/i');
    await expect(providerSection.first()).toBeVisible({ timeout: 10_000 });

    expect(errors).toEqual([]);
  });
});

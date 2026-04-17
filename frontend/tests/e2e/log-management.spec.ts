import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe.configure({ mode: 'serial' });

async function login(page: any) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

test.describe('Ladevorgänge anlegen und bearbeiten', () => {
  test.beforeAll(async () => {
    // Sauberer Zustand: alle Logs des Testfahrzeugs löschen
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    const authResp = await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    });
    const { token } = await authResp.json();

    const carsResp = await api.get('/api/cars', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const cars = await carsResp.json();
    if (!cars.length) return;

    const carId = cars[0].id;
    const logsResp = await api.get(`/api/logs?carId=${carId}&limit=50`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const logs = await logsResp.json();
    const ids: string[] = logs.map((l: any) => l.id);

    if (ids.length > 0) {
      await api.delete('/api/logs/batch', {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: ids,
      });
    }
  });

  test.beforeEach(async ({ page }) => {
    // Vor dem ersten Seitenaufruf setzen damit Vue beim Mount schon "alles gesehen" liest
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);

    await login(page);
  });

  test('Ladevorgang mit Pflichtfeldern erfassen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    await page.locator('input[placeholder="z.B. 42.5"]').fill('35.5');
    await page.locator('input[placeholder="z.B. 12.50"]').fill('8.90');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang mit "kWh laut Fahrzeug" erfassen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    await page.locator('input[placeholder="z.B. 42.5"]').fill('40.0');
    await page.locator('input[placeholder="z.B. 12.50"]').fill('10.00');
    await page.locator('#kwh-at-vehicle').fill('37.5');

    // Eindeutiger Zeitstempel um Duplikat-Kollision mit Test 1 zu vermeiden
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    await page.locator('input[type="datetime-local"]').fill(yesterday.toISOString().slice(0, 16));

    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang bearbeiten - kWh laut Fahrzeug nachtragen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    const editButton = page.locator('[title="Ladevorgang bearbeiten"]').first();
    await expect(editButton).toBeVisible({ timeout: 10_000 });
    await editButton.click();

    await expect(page.locator('label:has-text("kWh laut Fahrzeug")')).toBeVisible({ timeout: 5_000 });
    await page.locator('#kwh-at-vehicle').fill('36.0');
    await page.locator('button:has-text("Speichern")').click();

    await expect(page.locator('label:has-text("kWh laut Fahrzeug")')).not.toBeVisible({ timeout: 5_000 });
    expect(errors).toEqual([]);
  });
});

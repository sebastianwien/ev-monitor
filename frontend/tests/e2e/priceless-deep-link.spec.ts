import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Einstieg aus der Monatsrückblick-Mail: /logs?car=<id>&nachtragen=preis öffnet direkt das
 * Modal "Preise nachtragen" und nimmt die Parameter danach aus der URL. Das Modal öffnet auch
 * ohne Ladungen ohne Preis (dann mit "alles erledigt"), deshalb braucht der Test keine Testdaten.
 */
test.describe('Deep-Link Preise nachtragen', () => {
  let carId = '';

  test.beforeAll(async () => {
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    const token = (await (await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    })).json()).token;
    carId = (await (await api.get('/api/cars', { headers: { Authorization: `Bearer ${token}` } })).json())[0].id;
    await api.dispose();
  });

  test.beforeEach(async ({ page }) => {
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);
    await page.goto('/login');
    await page.locator('input[type="text"]').fill(TEST_USER.email);
    await page.locator('input[type="password"]').fill(TEST_USER.password);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
  });

  test('öffnet das Modal und räumt die URL auf', async ({ page }) => {
    await page.goto(`/logs?utm_source=email&car=${carId}&nachtragen=preis`);

    const modal = page.locator('[data-testid="priceless-logs-modal"]');
    await expect(modal).toBeVisible({ timeout: 10_000 });
    await expect(page).not.toHaveURL(/nachtragen=/);
    await expect(page).toHaveURL(/utm_source=email/);
  });

  test('normaler Besuch öffnet kein Modal', async ({ page }) => {
    await page.goto('/logs');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="priceless-logs-modal"]')).toBeHidden();
  });
});

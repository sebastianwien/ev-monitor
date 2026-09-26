import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Papierkorb: ein gelöschter Ladevorgang erscheint als Zeile unter der Liste und lässt sich
 * im Sheet wiederherstellen oder endgültig entfernen.
 */
test.describe('Gelöschte Ladevorgänge', () => {
  test.describe.configure({ mode: 'serial' });

  const ids: string[] = [];
  let token = '';
  let carId = '';

  test.beforeAll(async () => {
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    token = (await (await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    })).json()).token;
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
    carId = (await (await api.get('/api/cars', { headers })).json())[0].id;

    // Alten Papierkorb leeren, damit die Zählung stimmt
    const old = await (await api.get(`/api/logs/deleted?carId=${carId}`, { headers })).json();
    for (const l of old) await api.delete(`/api/logs/${l.id}/purge`, { headers });

    // Zwei Ladungen weit in der Vergangenheit anlegen und löschen. Zufällige Minute, damit parallele
    // Projekte und Wiederholungen nicht auf denselben Zeitpunkt fallen.
    const minute = Math.floor(Math.random() * 60 * 24);
    for (const day of [3, 4]) {
      const loggedAt = new Date(Date.UTC(2020, Math.floor(Math.random() * 12), day, Math.floor(minute / 60), minute % 60));
      const resp = await api.post('/api/logs', {
        headers,
        data: { carId, kwhCharged: 11.5, costEur: 3, odometerKm: 1000 + day, chargingType: 'AC',
          loggedAt: loggedAt.toISOString().slice(0, 19) },
      });
      expect(resp.ok()).toBeTruthy();
      const id = (await resp.json()).log.id;
      ids.push(id);
      expect((await api.delete(`/api/logs/${id}`, { headers })).ok()).toBeTruthy();
    }
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

  test('Zeile zeigt die Anzahl, Wiederherstellen und endgültig Entfernen leeren den Papierkorb', async ({ page }) => {
    await page.goto('/logs');
    const row = page.locator('[data-testid="deleted-logs-row"]');
    await expect(row).toContainText('2 gelöschte Ladevorgänge', { timeout: 10_000 });

    await row.click();
    const sheet = page.locator('[data-testid="deleted-logs-sheet"]');
    await expect(sheet.locator('[data-testid="deleted-log-row"]')).toHaveCount(2);

    await sheet.locator('[data-testid="deleted-log-restore"]').first().click();
    await expect(sheet.locator('[data-testid="deleted-log-row"]')).toHaveCount(1);

    await sheet.locator('[data-testid="deleted-log-purge"]').click();
    await sheet.locator('[data-testid="deleted-log-purge-confirm"]').click();
    await expect(sheet.locator('[data-testid="deleted-log-row"]')).toHaveCount(0);
    await expect(sheet).toContainText('Keine gelöschten Ladevorgänge');
  });

  test.afterAll(async () => {
    // Wiederhergestellte Ladung wegräumen, ohne neuen Tombstone zu hinterlassen
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    const headers = { Authorization: `Bearer ${token}` };
    for (const id of ids) {
      await api.delete(`/api/logs/${id}`, { headers });
      await api.delete(`/api/logs/${id}/purge`, { headers });
    }
    await api.dispose();
  });
});

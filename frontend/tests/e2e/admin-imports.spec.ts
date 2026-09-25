import { test, expect, type Page } from '@playwright/test';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

/**
 * Admin-Tab "Importe" (Import-Protokoll, Herstellerarchitektur R2b). Reiner FE-Test: das Backend
 * wird gemockt, der Admin-Zugang über ein unsigniertes JWT hergestellt (das FE dekodiert nur, die
 * Rechteprüfung macht das Backend - dort deckt sie AdminImportStatsControllerIntegrationTest ab).
 */

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const ADMIN_TOKEN = [
  b64url({ alg: 'none', typ: 'JWT' }),
  b64url({
    sub: 'admin@e2e.local', iat: now, exp: now + 3600, userId: '00000000-0000-0000-0000-00000000a001',
    username: 'e2e-admin', demoAccount: false, authProvider: 'LOCAL', role: 'ADMIN', premium: true,
  }),
  'sig',
].join('.');

const iso = (msAgo: number) => new Date(Date.now() - msAgo).toISOString();
const today = new Date();
const isoDay = (daysAgo: number) => {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const STATS = {
  days: 30,
  groups: [
    {
      provider: 'TESLA', channel: 'LIVE', health: 'OK', events: 120, errorRate: 0,
      sessionsImported: 110, sessionsSkipped: 10, sessionsFailed: 0, tripsImported: 300, tripsSkipped: 4,
      outcomes: { IMPORTED: 110, NO_NEW_DATA: 10 }, lastEventAt: iso(600_000), lastSuccessAt: iso(600_000),
      topErrors: [],
    },
    {
      provider: 'VW_GROUP', channel: 'SYNC', health: 'ERROR', events: 20, errorRate: 0.5,
      sessionsImported: 4, sessionsSkipped: 6, sessionsFailed: 0, tripsImported: 0, tripsSkipped: 0,
      outcomes: { IMPORTED: 4, NO_NEW_DATA: 6, PARSE_ERROR: 10 },
      lastEventAt: iso(60_000), lastSuccessAt: iso(3 * 3600_000),
      topErrors: [
        { error: 'VwEudaUnreadableException: Format wird nicht unterstuetzt - die Datei enthaelt keine erkennbaren Ladedaten', count: 10, lastAt: iso(60_000) },
      ],
    },
  ],
  daily: [
    { date: isoDay(1), outcome: 'IMPORTED', count: 60 },
    { date: isoDay(0), outcome: 'IMPORTED', count: 54 },
    { date: isoDay(0), outcome: 'PARSE_ERROR', count: 10 },
  ],
};

async function openImportsTab(page: Page, stats: object = STATS) {
  const requestedDays: string[] = [];
  await page.addInitScript(({ token, keys }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('onboarding-completed-admin@e2e.local', 'true');
    localStorage.setItem('seen-announcements', JSON.stringify(keys));
  }, { token: ADMIN_TOKEN, keys: featureAnnouncements.map((a) => a.key) });
  // Alles andere, was die Admin-Seite nebenbei vom Backend lädt, bekommt eine leere Liste.
  // Nur echte API-Pfade: ein Glob wie **/api/** träfe auch Vite-Module unter /src/api/.
  await page.route((url) => url.pathname.startsWith('/api/'), (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route((url) => url.pathname === '/api/admin/stats/imports', (route) => {
    requestedDays.push(new URL(route.request().url()).searchParams.get('days') ?? '');
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(stats) });
  });
  await page.goto('/admin?tab=imports');
  return requestedDays;
}

test.describe('Admin: Importe', () => {
  test('zeigt je Quelle eine Karte, gestörte zuerst, Ampel in Worten', async ({ page }) => {
    const requestedDays = await openImportsTab(page);

    await expect(page.getByRole('button', { name: 'Importe', exact: true })).toBeVisible();
    const cards = page.getByTestId('import-source-card');
    await expect(cards).toHaveCount(2);
    await expect(cards.first()).toContainText('VW Group · Sync');
    await expect(cards.first()).toContainText('Gestört');
    await expect(cards.first()).toContainText('50 %');
    await expect(cards.first()).toContainText('VwEudaUnreadableException');
    await expect(cards.first()).toContainText('Verbindungen');
    await expect(cards.first().getByTestId('import-connections')).toHaveText('–');
    await expect(cards.nth(1)).toContainText('Tesla · Live');
    await expect(cards.nth(1)).toContainText('OK');
    expect(requestedDays).toEqual(['30']);
  });

  test('Zeitraum 7 Tage lädt neu', async ({ page }) => {
    const requestedDays = await openImportsTab(page);
    await expect(page.getByTestId('import-source-card')).toHaveCount(2);

    await page.getByRole('button', { name: '7 Tage' }).click();

    await expect.poll(() => requestedDays).toEqual(['30', '7']);
  });

  test('ohne Importe: Leerzustand', async ({ page }) => {
    await openImportsTab(page, { days: 30, groups: [], daily: [] });

    await expect(page.getByText('Keine Importe im Zeitraum.')).toBeVisible();
    await expect(page.getByTestId('import-source-card')).toHaveCount(0);
  });

  test('kein horizontales Scrollen, Tabs scrollen für sich', async ({ page }) => {
    await openImportsTab(page);
    await expect(page.getByTestId('import-source-card')).toHaveCount(2);

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

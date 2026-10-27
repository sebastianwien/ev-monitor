import { test, expect, request, type APIRequestContext } from '@playwright/test';
import { ensureVerifiedUser, ensureTestCar, type E2eUser } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';
import { waitForStory } from './fixtures/story';

/**
 * "Teurer als üblich" ohne gestellte Daten: fünf andere Fahrer laden über die API bei IONITY,
 * ein eigener Fahrer zahlt deutlich mehr. Echt sind Ladungen, Vergleich je Betreiber, Karten
 * und Sheet. Gestellt ist nur die Rangliste: ein frischer Testnutzer steht dort schnell auf
 * Platz 1 bis 3, und dieser Moment würde den geprüften verdrängen.
 *
 * Die Nutzer bleiben in der Datenbank und werden beim nächsten Lauf wiederverwendet.
 */
const API_URL = process.env.API_URL || 'http://localhost:8080';
const PASSWORD = 'PlaywrightTest123!';
const OTHER_PRICES = [0.39, 0.45, 0.49, 0.52, 0.59];
const OTHERS: E2eUser[] = OTHER_PRICES.map((_, i) => ({ email: `bench-e2e-${i + 1}@playwright.local`, username: `bench_e2e_${i + 1}`, password: PASSWORD }));
const ME: E2eUser = { email: 'bench-e2e-me@playwright.local', username: 'bench_e2e_me', password: PASSWORD };
const MY_PRICE = 0.89;
const HOUR = 3_600_000;
const DAY = 24 * HOUR;

/** loggedAt ist beim Backend eine Ortszeit ohne Zone. */
const localIso = (ms: number) => new Date(ms - new Date(ms).getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
const auth = (jwt: string) => ({ Authorization: `Bearer ${jwt}` });

async function chargeAtIonity(api: APIRequestContext, jwt: string, carId: string, pricePerKwh: number, at: number) {
  const res = await api.post('/api/logs', {
    headers: auth(jwt),
    data: { carId, kwhCharged: 40, costEur: Math.round(40 * pricePerKwh * 100) / 100, loggedAt: localIso(at), chargingType: 'DC', isPublicCharging: true, cpoName: 'IONITY', socBeforeChargePercent: 20, socAfterChargePercent: 80 },
  });
  expect(res.ok(), `Ladung anlegen: ${res.status()} ${await res.text()}`).toBeTruthy();
}

/** Je anderer Fahrer reicht eine IONITY-Ladung im letzten Monat; nur wenn sie fehlt, kommt eine dazu. */
async function ensureRecentIonityCharge(api: APIRequestContext, user: E2eUser, price: number) {
  const jwt = await ensureVerifiedUser(api, user);
  const carId = await ensureTestCar(api, jwt);
  const logs = await (await api.get(`/api/logs?carId=${carId}&limit=10`, { headers: auth(jwt) })).json();
  const recent = Array.isArray(logs) && logs.some((l: any) => l.cpoName === 'IONITY' && Date.now() - Date.parse(l.loggedAt) < 30 * DAY);
  if (!recent) await chargeAtIonity(api, jwt, carId, price, Date.now() - 2 * DAY);
}

test('Teurer als üblich: echter Vergleich mit anderen Fahrern, Satz und Sheet', async ({ page }, testInfo) => {
  // Schreibt echte Daten desselben Nutzers: parallel in mehreren Browsern würden sich die Läufe stören.
  test.skip(testInfo.project.name !== 'chromium', 'nur einmal je Lauf, auf Chromium');
  test.setTimeout(120_000);
  const api = await request.newContext({ baseURL: API_URL });
  for (const [i, user] of OTHERS.entries()) await ensureRecentIonityCharge(api, user, OTHER_PRICES[i]);

  // Eigene Ladungen frisch: genau eine teure, älter als "gerade geladen" (24 h für Erfasstes), jünger als 3 Tage.
  const jwt = await ensureVerifiedUser(api, ME);
  const carId = await ensureTestCar(api, jwt);
  const mine = await (await api.get(`/api/logs?carId=${carId}&limit=100`, { headers: auth(jwt) })).json();
  for (const l of Array.isArray(mine) ? mine : []) await api.delete(`/api/logs/${l.id}`, { headers: auth(jwt) });
  const chargedAt = Date.now() - 30 * HOUR;
  await chargeAtIonity(api, jwt, carId, MY_PRICE, chargedAt);

  // Der Rückblick auf den Vormonat (mit dieser Ladung) läge sonst als Karte davor.
  const prev = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  const monthChangeKey = `month_change:${prev.getFullYear()}-${prev.getMonth() + 1}`;
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript(([seenKeys, key]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
    localStorage.setItem('story_dashboard_opt_in', 'true');
    localStorage.setItem('story_moments_seen', JSON.stringify({ shown: {}, dismissed: { [key]: Date.now() + 10 * 86_400_000 } }));
  }, [keys, monthChangeKey] as const);
  await page.route(/\/api\/public\/leaderboard\/standings\/me/, route => route.fulfill({ json: [] }));

  const benchmark = page.waitForResponse(r => r.url().includes('/api/charging-benchmarks'), { timeout: 30_000 });
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(ME.email);
  await page.locator('input[type="password"]').fill(ME.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/(dashboard|story)/, { timeout: 10_000 });
  if (!/\/story/.test(page.url())) await page.goto('/story');
  expect((await benchmark).status(), 'das Backend liefert einen Vergleich').toBe(200);
  await waitForStory(page);
  const card = page.getByTestId('story-news-expensive');
  await expect(card.getByTestId('story-news-sentence')).toContainText(/89\sct\/kWh bei IONITY\. Andere Fahrer zahlen dort im Schnitt \d+\sct\/kWh/);
  await card.getByRole('button', { name: 'Preise anderer Fahrer ansehen' }).click();
  const sheet = page.getByTestId('story-price-sheet');
  await expect(sheet).toContainText(/Du hast 89,0\sct\/kWh gezahlt\./);
  await expect(sheet).toContainText(/\d+ Fahrer, Schnellladen \(DC\), letzte 12 Monate/);
});

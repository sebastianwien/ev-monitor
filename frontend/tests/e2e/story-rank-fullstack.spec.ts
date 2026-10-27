import { test, expect, request } from '@playwright/test';
import { ensureVerifiedUser, ensureTestCar, type E2eUser } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';
import { waitForStory } from './fixtures/story';

/**
 * Rang ohne gestellte Rangliste: ein Nutzer mit einer Ladung im Monat steht in mindestens einer
 * Kategorie auf einem Platz. Die Kategorie-Listen nennen den eigenen Platz nur ab Platz 11,
 * deshalb liest die Story standings/me. Geprüft wird der echte Vertrag zwischen Backend und
 * Story: die Antwort trägt einen Platz, und die Seite holt genau diese Antwort.
 */
const API_URL = process.env.API_URL || 'http://localhost:8080';
const ME: E2eUser = { email: 'rank-e2e-me@playwright.local', username: 'rank_e2e_me', password: 'PlaywrightTest123!' };
const HOUR = 3_600_000;
const localIso = (ms: number) => new Date(ms - new Date(ms).getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
const auth = (jwt: string) => ({ Authorization: `Bearer ${jwt}` });

test('Rang: standings/me nennt den eigenen Platz, die Story liest ihn', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'nur einmal je Lauf, auf Chromium');
  test.setTimeout(90_000);
  const api = await request.newContext({ baseURL: API_URL });
  const jwt = await ensureVerifiedUser(api, ME);
  const carId = await ensureTestCar(api, jwt);

  // Eine Ladung in der Mitte des Vormonats reicht für einen Platz im Endstand des Vormonats. Das
  // Ranking des laufenden Monats wird bis zu 15 Minuten gecacht und wäre nach einer frischen Ladung
  // noch leer; der Vormonat wird selten abgefragt und ist deshalb verlässlich.
  const now = new Date();
  const prevStart = new Date(now.getFullYear(), now.getMonth() - 1, 1).getTime();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const logs = await (await api.get(`/api/logs?carId=${carId}&limit=10`, { headers: auth(jwt) })).json();
  const inPrev = Array.isArray(logs) && logs.some((l: any) => Date.parse(l.loggedAt) >= prevStart && Date.parse(l.loggedAt) < monthStart);
  if (!inPrev) {
    const res = await api.post('/api/logs', {
      headers: auth(jwt),
      data: { carId, kwhCharged: 30, costEur: 9, loggedAt: localIso(prevStart + 14 * 24 * HOUR), chargingType: 'AC', isPublicCharging: false, socBeforeChargePercent: 30, socAfterChargePercent: 80 },
    });
    expect(res.ok(), `Ladung anlegen: ${res.status()} ${await res.text()}`).toBeTruthy();
  }

  const standings = await (await api.get('/api/public/leaderboard/standings/me?monthsBack=1', { headers: auth(jwt) })).json();
  const ranked = (standings as Array<{ category: string; rank: number | null; value: number | null }>).filter(s => s.rank != null && s.value != null);
  expect(ranked.length, 'mindestens eine Kategorie mit Platz im Vormonat (Ranking-Cache 15 min)').toBeGreaterThan(0);
  expect(ranked.map(s => s.category)).toContain('MONTHLY_CHARGES');
  expect((await api.get('/api/public/leaderboard/standings/me', { headers: auth(jwt) })).status()).toBe(200);
  expect((await api.get('/api/public/leaderboard/standings/me?monthsBack=2', { headers: auth(jwt) })).status()).toBe(400);
  expect((await api.get('/api/public/leaderboard/standings/me?monthsBack=1')).status(), 'ohne Login kein Stand').not.toBe(200);

  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript((seenKeys) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
    localStorage.setItem('story_dashboard_opt_in', 'true');
  }, keys);

  const standingsCall = page.waitForResponse(r => r.url().includes('/api/public/leaderboard/standings/me'), { timeout: 30_000 });
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(ME.email);
  await page.locator('input[type="password"]').fill(ME.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/(dashboard|story)/, { timeout: 10_000 });
  if (!/\/story/.test(page.url())) await page.goto('/story');
  const res = await standingsCall;
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
  await waitForStory(page);
});

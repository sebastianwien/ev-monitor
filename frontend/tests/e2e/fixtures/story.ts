import { expect, type Page, type Route } from '@playwright/test';
import { TEST_USER } from '../global-setup';
import { featureAnnouncements } from '../../../src/config/featureAnnouncements';

/**
 * Feste Daten für die Story-Seite: Uhr und alle Antworten, aus denen Hero, Kacheln und Feed
 * entstehen, kommen von hier. Ausgangslage ist ein ruhiger Oktober ohne Moment (Monats-Hero);
 * jeder Test überschreibt nur, was seinen Zustand auslöst. Echt bleiben Login, Auto und Navigation.
 */

/** Mittwoch, 14.10.2026, 12:00 Ortszeit: kein Monatswechsel, alle Zeitfenster frei. */
export const NOW_MID = new Date(2026, 9, 14, 12, 0).getTime();
/** Samstag, 03.10.2026: Monatsrückblick auf September. */
export const NOW_MONTH_START = new Date(2026, 9, 3, 10, 0).getTime();

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const iso = (ms: number) => new Date(ms).toISOString();
const isoDate = (ms: number) => {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export interface StoryData {
  now: number
  logs: unknown[]
  priceless: unknown[]
  soh: unknown[]
  trips: unknown[]
  ranks: Record<string, { rank: number; value: number; unit: string } | null>
  monthStats: unknown
  previousStats: unknown
  recentStats: unknown
  seriesStats: unknown
  lifetime: unknown
  live: unknown | null
  benchmark: unknown | null
}

const stats = (over: Record<string, unknown> = {}) => ({
  totalKwhCharged: 52, energyCostEur: 33, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur: 33,
  avgCostPerKwh: 0.29, cheapestChargeEur: 2.1, mostExpensiveChargeEur: 9.8, avgChargeDurationMinutes: 180,
  totalCharges: 6, totalDistanceKm: 274, avgConsumptionKwhPer100km: 18.2, estimatedConsumptionCount: 0,
  summerConsumptionKwhPer100km: 15.5, winterConsumptionKwhPer100km: 20.5,
  chargesOverTime: [], chargingTypeSplit: null, locationSplit: null, peerBenchmark: null, chargingEfficiencySplit: null,
  ...over,
});

const point = (ms: number, over: Record<string, unknown> = {}) => ({
  timestamp: isoDate(ms), costEur: 5.5, kwhCharged: 9, distanceKm: 46, consumptionKwhPer100km: 18.2, charges: 1, ...over,
});

/** Eine Ladung wie /api/logs sie liefert. Standard: zu Hause erfasst, mit Preis. */
export const log = (now: number, over: Record<string, unknown> = {}) => ({
  id: '00000000-0000-0000-0000-0000000c0001', carId: null, kwhCharged: 21, kwhAtVehicle: 20, costEur: 6.1,
  socBeforeChargePercent: 30, socAfterChargePercent: 80, loggedAt: iso(now - 5 * DAY), createdAt: iso(now - 5 * DAY),
  dataSource: 'USER_LOGGED', includeInStatistics: true, isPublicCharging: false, cpoName: null, chargingType: 'AC',
  ...over,
});

export const trip = (now: number, over: Record<string, unknown> = {}) => ({
  id: '00000000-0000-0000-0000-0000000f0001', tripStartedAt: iso(now - 10 * HOUR), tripEndedAt: iso(now - 9.5 * HOUR),
  distanceKm: 31, outsideTempCelsius: 12, consumptionKwhPer100km: 17.5, ...over,
});

/** Ruhiger Monat: Ladungen mit Preis, kein Ereignis in den Zeitfenstern der Momente. */
export function calmMonth(now: number = NOW_MID): StoryData {
  const first = new Date(now); first.setDate(1); first.setHours(12, 0, 0, 0);
  return {
    now,
    logs: [log(now)],
    priceless: [],
    soh: [],
    trips: [],
    ranks: {},
    monthStats: stats({ chargesOverTime: [1, 3, 6, 9, 12].map(d => point(first.getTime() + (d - 1) * DAY)) }),
    previousStats: stats({ totalDistanceKm: 512, energyCostEur: 61, totalCostEur: 61, totalCharges: 11, totalKwhCharged: 95 }),
    recentStats: stats(),
    seriesStats: stats({ chargesOverTime: [5, 4, 3, 2, 1, 0].map(m => point(new Date(first.getFullYear(), first.getMonth() - m, 1).getTime(), { costEur: 40 + m * 3 })) }),
    // 50 Ladungen seit März 2025: kein Meilenstein, kein Jahrestag in der Nähe.
    lifetime: stats({ chargesOverTime: [point(new Date(2025, 2, 1).getTime(), { charges: 45, distanceKm: 4000 }), point(now - 5 * DAY, { charges: 5 })] }),
    live: null,
    benchmark: null,
  };
}

function json(route: Route, body: unknown) {
  return body == null ? route.fulfill({ status: 204, body: '' }) : route.fulfill({ json: body });
}

/** Alle Story-Abfragen auf feste Daten umleiten. Die Statistik unterscheidet sich nur in den Parametern. */
export async function mockStory(page: Page, data: StoryData) {
  const today = new Date(data.now); today.setHours(0, 0, 0, 0);
  const recentStart = isoDate(today.getTime() - 27 * DAY);
  const m = new Date(data.now);
  const prevStart = isoDate(new Date(m.getFullYear(), m.getMonth() - 1, 1).getTime());

  await page.route(/\/api\/logs\/statistics\?/, route => {
    const p = new URL(route.request().url()).searchParams;
    if (!p.get('timeRange')) return json(route, data.lifetime);
    if (p.get('groupBy') === 'DAY') return json(route, data.monthStats);
    if (p.get('startDate') === recentStart) return json(route, data.recentStats);
    if (p.get('startDate') === prevStart) return json(route, data.previousStats);
    return json(route, data.seriesStats);
  });
  await page.route(/\/api\/logs\?carId=[^&]+&limit=10$/, route => json(route, data.logs));
  await page.route(/\/api\/logs\/priceless\?/, route => json(route, data.priceless));
  await page.route(/\/api\/logs\/[^/]+\/peer-model-comparison/, route => json(route, { modelComparisons: [] }));
  await page.route(/\/api\/cars\/[^/]+\/soh$/, route => json(route, data.soh));
  await page.route(/\/api\/cars\/[^/]+\/live\/charging\/history/, route => json(route, { points: [] }));
  await page.route(/\/api\/cars\/[^/]+\/live\/charging$/, route => json(route, data.live ?? { isActive: false }));
  await page.route(/\/api\/trips\?/, route => {
    const limit = new URL(route.request().url()).searchParams.get('limit');
    return json(route, limit ? data.trips.slice(0, Number(limit)) : data.trips);
  });
  await page.route(/\/api\/public\/leaderboard\/[A-Z_]+/, route => {
    const category = new URL(route.request().url()).pathname.split('/').pop()!;
    return json(route, { ownEntry: data.ranks[category] ?? null, entries: [] });
  });
  await page.route(/\/api\/charging-benchmarks\?/, route => json(route, data.benchmark));
  await page.route(/\/api\/stats\/charging-savings/, route => json(route, null));
  await page.route(/\/api\/public\/fuel-prices/, route => json(route, { petrolEurPerLitre: 1.75, dieselEurPerLitre: 1.65, updatedAt: iso(data.now - 6 * HOUR) }));
}

/** Uhr stellen, Daten umleiten, einloggen, /story öffnen. Hinweise und Experiment-Notiz sind schon gesehen. */
export async function openStory(page: Page, data: StoryData, opts: { theme?: 'light' | 'dark' } = {}) {
  await page.clock.setFixedTime(data.now);
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript(([seenKeys, theme]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
    localStorage.setItem('story_dashboard_opt_in', 'true');
    localStorage.setItem('theme', theme);
  }, [keys, opts.theme ?? 'light'] as const);
  await mockStory(page, data);
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/(dashboard|story)/, { timeout: 10_000 });
  if (!/\/story/.test(page.url())) await page.goto('/story');
  await waitForStory(page);
}

/**
 * Fertig geladen heisst: Skeleton der ersten Box weg. networkidle reicht nicht, nach dem Login
 * wechselt die App per Client-Routing auf /story, ohne neues Dokument.
 */
export async function waitForStory(page: Page) {
  await expect(page.getByTestId('story-view')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('story-hero-box').locator('[aria-busy="true"]')).toHaveCount(0, { timeout: 20_000 });
}

export { HOUR, DAY, iso, isoDate, point, stats };

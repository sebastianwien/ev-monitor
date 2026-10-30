import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, log, trip, point, stats, NOW_MID, DAY, HOUR, iso, type StoryData } from './fixtures/story';

/**
 * Gates und Angebote im neuen Design (Story und Logbuch), je auf 390 px und 1440 px: Strom-Sheet,
 * Kosten-Sheet, leerer Monat, Meilenstein, Ladeorte, Logbuch. Der Testnutzer hat kein Abo; der
 * Probemonat hängt am Registrierungstag aus dem JWT, darum steht die Uhr im Probemonat-Fall vor dessen Ende.
 * Mit SHOTS=<ordner> legt jeder Fall ein Bild ab.
 */
const SHOTS = process.env.SHOTS;
const VIEWS = [{ name: 'mobil', width: 390, height: 844 }, { name: 'desktop', width: 1440, height: 900 }] as const;

/** Auto des Testnutzers mit gewählter Marke, als einziges in der Garage */
async function brand(page: Page, b: 'TESLA' | 'VW') {
  await page.route(/\/api\/cars$/, async route => {
    const res = await route.fetch();
    const cars = await res.json();
    await route.fulfill({ response: res, json: [{ ...cars[0], brand: b, model: b === 'TESLA' ? 'MODEL_3' : 'ID_3', displayName: b === 'TESLA' ? 'Tesla Model 3' : 'VW ID.3', batteryCapacityKwh: 60, effectiveBatteryCapacityKwh: 60 }] });
  });
}
const at = (day: number, h: number) => new Date(2026, 9, day, h).getTime();
/** Fahrten mit Akkustand: Fahren und Stand lassen sich daraus rechnen */
function trips(): unknown[] {
  // Älteste zuerst, damit der Kilometerstand durchläuft: zwischen den Fahrten steht das Auto (Standverlust)
  let odo = 24000
  return [7, 10, 12].flatMap((day, i) => [[8, 80, 75], [17, 72, 67]].map(([h, s0, s1], j) => {
    const start = odo; odo += 18
    return trip(NOW_MID, {
      id: `00000000-0000-0000-0000-0000000b${i}00${j + 1}`, tripStartedAt: iso(at(day, h)), tripEndedAt: iso(at(day, h) + 30 * 60_000),
      distanceKm: 18, socStart: s0, socEnd: s1, odometerStartKm: start, odometerEndKm: odo,
    })
  })).reverse()
}
async function shot(page: Page, view: string, name: string) {
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}-${view}.png` });
}
const savings = (page: Page, body: { status: number; json?: unknown }) =>
  page.route(/\/api\/stats\/charging-savings/, route => route.fulfill(body));

for (const v of VIEWS) {
  test.describe(v.name, () => {
    test.use({ viewport: { width: v.width, height: v.height } });

    test('Strom-Sheet gesperrt, Tesla: Supporter-Pack', async ({ page }) => {
      await brand(page, 'TESLA');
      await openStory(page, { ...calmMonth(), trips: trips() });
      await page.getByTestId('story-tile-flow').click();
      const row = page.getByTestId('story-upsell-flow');
      await expect(row).toHaveAttribute('href', '/supporter?from=story_flow');
      await row.scrollIntoViewIfNeeded();
      await shot(page, v.name, '01-strom-gesperrt-tesla');
    });

    test('Strom-Sheet gesperrt, VW: AutoSync', async ({ page }) => {
      await brand(page, 'VW');
      await openStory(page, { ...calmMonth(), trips: trips() });
      await page.getByTestId('story-tile-flow').click();
      const row = page.getByTestId('story-upsell-flow');
      await expect(row).toHaveAttribute('href', '/upgrade?from=story_flow');
      await row.scrollIntoViewIfNeeded();
      await shot(page, v.name, '02-strom-gesperrt-vw');
    });

    test('Strom-Sheet im Probemonat', async ({ page }) => {
      await brand(page, 'TESLA');
      // Probemonat ab Start 04.09. für alle, die vorher da waren: am 20.09. läuft er noch
      const now = new Date(2026, 8, 20, 12).getTime();
      const d = calmMonth(now);
      d.trips = [18, 16, 14].flatMap((day, i) => [
        trip(now, { id: `00000000-0000-0000-0000-0000000c${i}001`, tripStartedAt: iso(new Date(2026, 8, day, 8).getTime()), tripEndedAt: iso(new Date(2026, 8, day, 8, 30).getTime()), distanceKm: 18, socStart: 80, socEnd: 75 }),
        trip(now, { id: `00000000-0000-0000-0000-0000000c${i}002`, tripStartedAt: iso(new Date(2026, 8, day, 17).getTime()), tripEndedAt: iso(new Date(2026, 8, day, 17, 30).getTime()), distanceKm: 18, socStart: 72, socEnd: 67 }),
      ]);
      await openStory(page, d);
      await page.getByTestId('story-tile-flow').click();
      await expect(page.getByTestId('story-trial-flow')).toContainText(/gratis bis/);
      await page.getByTestId('story-trial-flow').scrollIntoViewIfNeeded();
      await shot(page, v.name, '03-strom-probemonat');
    });

    test('Kosten-Sheet: Heimlade-Ersparnis gesperrt', async ({ page }) => {
      await brand(page, 'VW');
      await openStory(page, calmMonth());
      await savings(page, { status: 403, json: {} });
      await page.reload();
      await page.getByTestId('story-tile-cost').click();
      const row = page.getByTestId('story-upsell-cost');
      await expect(row).toHaveAttribute('href', '/upgrade?from=story_cost');
      await row.scrollIntoViewIfNeeded();
      await shot(page, v.name, '04-kosten-gesperrt');
    });

    test('Kosten-Sheet: Heimlade-Ersparnis im Probemonat', async ({ page }) => {
      await brand(page, 'TESLA');
      await openStory(page, calmMonth());
      await savings(page, { status: 200, json: { savingsEur: 84, viaTrial: true, trialEndsAt: '2026-11-03' } });
      await page.reload();
      await page.getByTestId('story-tile-cost').click();
      await expect(page.getByTestId('story-trial-cost')).toContainText('3. November');
      await page.getByTestId('story-trial-cost').scrollIntoViewIfNeeded();
      await shot(page, v.name, '05-kosten-probemonat');
    });

    test('Leerer Monat: AutoSync', async ({ page }) => {
      await brand(page, 'VW');
      const d = calmMonth();
      d.monthStats = stats({ totalCharges: 0, totalKwhCharged: 0, energyCostEur: 0, totalCostEur: 0, totalDistanceKm: 0, chargesOverTime: [] });
      d.logs = [log(NOW_MID, { loggedAt: iso(NOW_MID - 10 * DAY), createdAt: iso(NOW_MID - 10 * DAY) })];
      await openStory(page, d);
      await expect(page.getByTestId('story-empty-autosync')).toContainText('kann Ladungen und Fahrten selbst eintragen');
      await shot(page, v.name, '06-leerer-monat-autosync');
    });

    test('Meilenstein: Projekt unterstützen', async ({ page }) => {
      await brand(page, 'TESLA');
      await openStory(page, {
        ...calmMonth(),
        lifetime: stats({ chargesOverTime: [point(new Date(2025, 2, 1).getTime(), { charges: 99, distanceKm: 4000 }), point(NOW_MID - DAY, { charges: 1, distanceKm: 40 })] }),
      });
      const card = page.getByTestId('story-news-milestone');
      await expect(card.getByRole('link', { name: 'Projekt unterstützen' })).toHaveAttribute('href', '/supporter?from=story_milestone');
      await page.waitForTimeout(1500); // Konfetti vorbei
      await shot(page, v.name, '07-meilenstein-supporter');
    });

    test('Ladeorte: nur die neueste Fahrt mit Linie', async ({ page }) => {
      await brand(page, 'TESLA');
      const line = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';
      const d: StoryData = {
        ...calmMonth(),
        trips: [trip(NOW_MID, { id: 'tr-1', tracePolyline: line }), trip(NOW_MID, { id: 'tr-2', tripStartedAt: iso(NOW_MID - 30 * HOUR), tripEndedAt: iso(NOW_MID - 29 * HOUR) })],
        places: [
          { geohash: 'u1x3k7q', kind: 'PUBLIC', charges: 2, kwh: 80, costEur: 30, pricePerKwh: 0.375, lastChargedAt: iso(NOW_MID - 2 * DAY), lastLogId: 'log-1', cpoName: 'Tesla Supercharger', chargingType: 'DC' },
          { geohash: 'u1x3k7r', kind: 'HOME', charges: 4, kwh: 90, costEur: 27, pricePerKwh: 0.3, lastChargedAt: iso(NOW_MID - DAY), lastLogId: 'log-2', cpoName: null, chargingType: 'AC' },
        ],
      };
      await openStory(page, d);
      await page.getByTestId('story-map-open').first().click();
      await page.getByRole('switch').first().click();
      const row = page.getByTestId('places-trips-locked');
      await expect(row).toHaveAttribute('href', '/supporter?from=story_places');
      await row.scrollIntoViewIfNeeded();
      await shot(page, v.name, '08-ladeorte-gesperrt');
    });

    test('Logbuch: Supporter-Banner, Standverlust-Vorgeschmack, Ladekurve gesperrt', async ({ page }) => {
      await brand(page, 'TESLA');
      const d = calmMonth();
      d.trips = trips();
      d.logs = [log(NOW_MID, { id: '00000000-0000-0000-0000-0000000d0001', loggedAt: iso(at(11, 20)), hasPowerCurve: true, dataSource: 'TESLA_FLEET_API', chargingType: 'DC', isPublicCharging: true, cpoName: 'Tesla Supercharger' })];
      await openStory(page, d);
      await page.route(/\/api\/trips\?/, route => route.fulfill({ json: d.trips }));
      await page.route(/\/api\/logs\?carId=[^&]+&(from=|limit=1$)/, route => route.fulfill({ json: d.logs }));
      await page.addInitScript(() => localStorage.removeItem('supporter_banner_dismissed'));
      await page.goto('/logs');
      await expect(page.getByTestId('logbook')).toBeVisible();
      await expect(page.getByTestId('supporter-banner')).toBeVisible();
      await shot(page, v.name, '09-logbuch-banner');
      const unlock = page.getByRole('link', { name: /freischalten/i }).first();
      if (await unlock.count()) {
        await expect(unlock).toHaveAttribute('href', '/supporter?from=logbook_drain');
        await unlock.scrollIntoViewIfNeeded();
        await shot(page, v.name, '10-logbuch-standverlust');
      }
      await page.getByTestId('day-row-charge').first().click();
      await expect(page.getByTestId('logbook-detail')).toBeVisible();
      await expect(page.getByTestId('logbook-curve')).toBeVisible();
      await page.waitForTimeout(500); // Sheet eingefahren
      await shot(page, v.name, '11-logbuch-ladekurve-schloss');
      await page.getByTestId('logbook-curve').click();
      await page.waitForTimeout(500);
      await shot(page, v.name, '12-logbuch-ladekurve-modal');
    });
  });
}

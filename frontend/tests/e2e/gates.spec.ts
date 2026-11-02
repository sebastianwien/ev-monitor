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
      await expect(page.getByTestId('story-trial-flow')).toContainText(/Inklusive bis/);
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
      await expect(page.getByTestId('story-trial-cost')).toContainText('3.11.');
      await page.getByTestId('story-trial-cost').scrollIntoViewIfNeeded();
      await shot(page, v.name, '05-kosten-probemonat');
    });

    test('Leerer Monat: AutoSync', async ({ page }) => {
      await brand(page, 'VW');
      const d = calmMonth();
      d.monthStats = stats({ totalCharges: 0, totalKwhCharged: 0, energyCostEur: 0, totalCostEur: 0, totalDistanceKm: 0, chargesOverTime: [] });
      d.logs = [log(NOW_MID, { loggedAt: iso(NOW_MID - 10 * DAY), createdAt: iso(NOW_MID - 10 * DAY) })];
      await openStory(page, d);
      await expect(page.getByTestId('story-empty-autosync')).toContainText('schickt Ladungen selbst an EV Monitor');
      // Leerer laufender Monat: der Rückblick steht oben, auch wenn seine Karte weggeklickt ist, und fehlt im Stapel.
      await expect(page.getByTestId('story-recap-hero')).toContainText(/September: 512\skm für 61\s€\./);
      await expect(page.getByTestId('recap-fact')).toHaveText([/pro 100\skm/, /95\skWh/, /11/]);
      await expect(page.getByTestId('story-recap-hero')).not.toContainText('teilen');
      await expect(page.getByTestId('story-news-month_change')).toHaveCount(0);
      await shot(page, v.name, '06-leerer-monat-autosync');
    });

    test('Leerer Wintermonat: Ausblick nur im laufenden Monat', async ({ page }) => {
      await brand(page, 'VW');
      const now = new Date(2026, 1, 10, 12).getTime();
      const d = calmMonth(now);
      d.monthStats = stats({ totalCharges: 0, totalKwhCharged: 0, energyCostEur: 0, totalCostEur: 0, totalDistanceKm: 0, chargesOverTime: [] });
      // Reichweite kommt vom Backend: Winter kürzer als Sommer, sonst gibt es keinen Ausblick
      const season = (km: number, consumption: number) => ({ rangeKm: km, consumptionKwhPer100km: consumption, source: 'OWN_DRIVE', scope: null });
      d.recentStats = stats({ range: { current: season(310, 19.4), summer: season(380, 15.8), winter: season(310, 19.4) } });
      d.previousStats = { ...d.previousStats, co2: { savedKg: 45.1, petrolKg: 93.8, electricityKg: 48.7, distanceKm: 512, petrolLiters: 38.4, petrolLitersPer100km: 7.5 } };
      d.logs = [log(now, { loggedAt: iso(now - 10 * DAY), createdAt: iso(now - 10 * DAY) })];
      // Wetter am Heimladeort: die nächsten 7 Tage kälter als die letzte Woche
      const days = [[-1.4, 6.4, 0], [0.2, 5.1, 3], [-0.6, 3.8, 61], [-2.2, 2.4, 71], [-1.1, 4.0, 2], [0.4, 5.5, 3], [1.0, 6.2, 0]]
        .map(([minC, maxC, code], i) => ({ date: `2026-02-${String(10 + i).padStart(2, '0')}`, minC, maxC, code, gustKmh: 20 }));
      await page.route(/\/api\/weather\/outlook/, route => route.fulfill({ json: { recentMeanC: 8.6, aheadMeanC: 4.3, deltaC: -4.3, recentDays: 7, aheadDays: 7, days } }));
      await openStory(page, d);
      await expect(page.getByTestId('story-winter-outlook')).toContainText(/Ausblick Februar/i);
      await expect(page.getByTestId('outlook-range')).toContainText(/Sommer.*~380\skm.*Winter.*~310\skm/);
      await expect(page.getByTestId('outlook-day')).toHaveCount(7);
      await expect(page.getByTestId('outlook-trend')).toHaveText('4° kälter als letzte Woche');
      // Im Rückblick-Kopf steht der Ausblick direkt unter den Zahlen, Normal- und Schnellladen gibt es dort nicht
      await expect(page.getByTestId('story-recap-hero').getByTestId('story-winter-outlook')).toBeVisible();
      await expect(page.getByTestId('story-recap-hero')).not.toContainText('Schnell (DC)');
      await expect(page.getByTestId('story-recap-hero')).toContainText(/Gegenüber einem Verbrenner hast du 45\skg\sCO₂ und 7\s€ gespart\. Klasse! 🎉/);
      await shot(page, v.name, '07-leerer-wintermonat');
      // Ein vergangener leerer Wintermonat bekommt keinen Ausblick mehr.
      await page.getByTestId('story-month-prev').first().click();
      await expect(page.getByTestId('story-month-label').first()).toHaveAttribute('aria-label', /Januar/);
      await expect(page.getByTestId('story-winter-outlook')).toHaveCount(0);
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

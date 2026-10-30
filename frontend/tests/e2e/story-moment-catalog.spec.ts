import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, log, trip, point, stats, NOW_MID, NOW_MONTH_START, HOUR, DAY, iso, isoDate, type StoryData } from './fixtures/story';

/**
 * Katalog aller Zustände der Story: je Zustand feste Daten, Prüfung von Satz und Handlung (Seite
 * oder Karte von unten), dazu ein Bild der ganzen Seite im Bericht (`npx playwright show-report`)
 * zum Durchblättern. Lokal vergleicht jeder Test den ersten Bildschirm (390 x 844, mit Karte) mit
 * dem freigegebenen Bild; in CI nicht, die Vergleichsbilder entstehen auf macOS und weichen unter
 * Linux in der Schrift ab.
 *
 * Bilder neu freigeben nach gewollter Änderung: `npx playwright test story-moment-catalog --update-snapshots`.
 */
test.use({ viewport: { width: 390, height: 844 } });

interface Case {
  name: string
  data: () => StoryData
  check: (page: Page) => Promise<void>
  /** Rückblick oder Ranglisten-Abschluss des Vormonats sehen wollen (sonst gelten sie als erledigt). */
  keep?: Array<'month_change' | 'rank'>
}

const card = (page: Page, kind: string) => page.getByTestId(`story-news-${kind}`);
const sentence = (page: Page, kind: string) => card(page, kind).getByTestId('story-news-sentence');

const CASES: Case[] = [
  {
    name: 'monat',
    data: () => calmMonth(),
    check: async (page) => {
      await expect(page.getByTestId('story-news')).toHaveCount(0);
      await expect(page.getByTestId('story-news-tab')).toHaveCount(0);
      await expect(page.getByTestId('story-hero-value')).toContainText(/Oktober: 274\skm für 33\s€\./);
      // 52 kWh bisher, September 95 kWh: Prognose statt "weniger als im Vormonat"
      await expect(page.getByTestId('story-hero-box')).toContainText(/Prognose ~60\s€, Vormonat 61\s€/);
    },
  },
  {
    name: 'monat-fixkosten',
    data: () => {
      const d = calmMonth();
      return { ...d, monthStats: { ...(d.monthStats as Record<string, unknown>), fixedCostEur: 163, totalCostEur: 196 } };
    },
    check: async (page) => {
      const toggle = page.getByTestId('story-hero-layers');
      await expect(toggle).toHaveText(/Alle Kosten 196\s€/);
      await expect(page.getByTestId('story-hero-value')).toContainText(/Oktober: 274\skm für 33\s€\./);
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    },
  },
  {
    name: 'gerade-geladen',
    data: () => ({ ...calmMonth(), logs: [log(NOW_MID, { loggedAt: iso(NOW_MID - 40 * 60_000), createdAt: iso(NOW_MID - 40 * 60_000), kwhAtVehicle: 28.5, kwhCharged: 30, socBeforeChargePercent: 35, socAfterChargePercent: 80, costEur: null })] }),
    check: async (page) => {
      // Keine Karte: die Zuletzt-Karte ist "Heute geladen" und bietet den Preis an.
      const today = page.getByTestId('story-latest-today');
      await expect(today).toContainText(/Heute geladen/);
      await expect(today).toContainText(/Akku jetzt 80\s%/);
      await expect(today).toContainText(/28,5\skWh ab 35\s%/);
      await expect(today.getByRole('button', { name: 'Preis eintragen' })).toBeVisible();
      await expect(page.getByTestId('story-news')).toHaveCount(0);
    },
  },
  {
    name: 'teurer-als-ueblich',
    data: () => ({
      ...calmMonth(),
      logs: [log(NOW_MID, { loggedAt: iso(NOW_MID - 20 * HOUR), createdAt: iso(NOW_MID - 20 * HOUR), kwhCharged: 40, kwhAtVehicle: 38, costEur: 31.6, isPublicCharging: true, cpoName: 'IONITY', chargingType: 'DC' })],
      benchmark: {
        cpoName: 'IONITY', chargingType: 'DC', drivers: 18, medianPricePerKwh: 0.52, lowerQuartilePricePerKwh: 0.39, upperQuartilePricePerKwh: 0.6,
        cards: [{ name: 'IONITY', variant: 'Motion', pricePerKwh: 0.49, monthlyFeeEur: 5.99, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' }],
      },
    }),
    check: async (page) => {
      await expect(sentence(page, 'expensive')).toContainText(/Gestern 79\sct\/kWh bei IONITY\. Andere Fahrer zahlen dort im Schnitt 52\sct\/kWh, mit einer Ladekarte ab 49\sct\/kWh\./);
      await expect(card(page, 'expensive').getByRole('button', { name: 'Preise anderer Fahrer ansehen' })).toBeVisible();
    },
  },
  {
    name: 'laedt-gerade',
    data: () => ({
      ...calmMonth(),
      live: {
        isActive: true, chargingType: 'DC', powerKw: 142, socPercent: 46, energyRemainingKwh: null, timeToFullMinutes: 22,
        estRangeKm: 230, chargeAmps: null, sessionStartedAt: iso(NOW_MID - 12 * 60_000), sessionEndedAt: null,
        socAtSessionStart: 18, chargeLimitSoc: 80, lastUpdatedAt: iso(NOW_MID - 20_000),
      },
    }),
    check: async (page) => {
      await expect(page.getByTestId('story-live-hero')).toBeVisible();
      await expect(page.getByTestId('story-live-hero')).toContainText('142');
    },
  },
  {
    name: 'lange-fahrt',
    data: () => ({
      ...calmMonth(),
      trips: [
        trip(NOW_MID, { id: '00000000-0000-0000-0000-0000000f0002', tripStartedAt: iso(NOW_MID - 16 * HOUR), tripEndedAt: iso(NOW_MID - 14 * HOUR), distanceKm: 190, outsideTempCelsius: 13, locationEndGeohash: 'u0yjjd6j' }),
        trip(NOW_MID, { id: '00000000-0000-0000-0000-0000000f0001', tripStartedAt: iso(NOW_MID - 20 * HOUR), tripEndedAt: iso(NOW_MID - 17 * HOUR), distanceKm: 230, outsideTempCelsius: 14 }),
      ],
      logs: [
        log(NOW_MID, { id: '00000000-0000-0000-0000-0000000c0004', loggedAt: iso(NOW_MID - 16.5 * HOUR), createdAt: iso(NOW_MID - 16.5 * HOUR), costEur: 18.5, isPublicCharging: true, chargingType: 'DC', kwhCharged: 40 }),
        log(NOW_MID, { id: '00000000-0000-0000-0000-0000000c0005', loggedAt: iso(NOW_MID - 17.5 * HOUR), createdAt: iso(NOW_MID - 17.5 * HOUR), costEur: 12.5, isPublicCharging: true, chargingType: 'DC', kwhCharged: 28 }),
      ],
    }),
    check: async (page) => {
      await expect(sentence(page, 'long_trip')).toContainText(/Gestern: 420\skm, 2\sLadestopps für 31\s€\./);
      const forecast = card(page, 'long_trip').getByTestId('story-news-forecast');
      await expect(forecast.getByRole('heading', { name: 'Wetter am Ziel' })).toBeVisible();
      await expect(forecast).toContainText(/Heute: Teils bewölkt, 4 bis 12\s°C\. Morgen: Sonne und Schauer, 2 bis 9\s°C\./);
      await expect(card(page, 'long_trip').getByRole('link', { name: 'Fahrten ansehen' })).toBeVisible();
    },
  },
  {
    name: 'es-wird-kalt',
    data: () => ({ ...calmMonth(), trips: [trip(NOW_MID, { tripStartedAt: iso(NOW_MID - 10 * HOUR), tripEndedAt: iso(NOW_MID - 9.5 * HOUR), outsideTempCelsius: 3 })] }),
    check: async (page) => {
      await expect(sentence(page, 'first_frost')).toContainText(/Heute 3\s°C\. Im Winter kommst du mit einer Akkuladung etwa [\d.]+\skm weit, im Sommer [\d.]+\skm\./);
      await expect(card(page, 'first_frost').getByRole('button', { name: 'Winterwerte ansehen' })).toBeVisible();
    },
  },
  {
    name: 'monatsrueckblick',
    keep: ['month_change'],
    data: () => ({
      ...calmMonth(NOW_MONTH_START),
      monthStats: stats({ totalCharges: 1, totalDistanceKm: 40, energyCostEur: 5, totalCostEur: 5, totalKwhCharged: 9, chargesOverTime: [point(new Date(2026, 9, 1, 12).getTime())] }),
      previousStats: stats({ totalDistanceKm: 512, energyCostEur: 61, totalCostEur: 61, totalCharges: 11, totalKwhCharged: 95 }),
    }),
    check: async (page) => {
      const review = card(page, 'month_change');
      await expect(sentence(page, 'month_change')).toContainText(/September: 512\skm für 61\s€\./);
      // 512 km x 7,6 l (Benziner der Klasse SEDAN, das E2E-Auto ist ein Model 3) x 1,75 € = 68 €
      await expect(review).toContainText(/Mit Benzin wären es 68\s€\./);
      await expect(review.getByRole('button', { name: 'Rückblick teilen' })).toBeVisible();
    },
  },
  {
    name: 'meilenstein',
    data: () => ({
      ...calmMonth(),
      lifetime: stats({ chargesOverTime: [point(new Date(2025, 2, 1).getTime(), { charges: 99, distanceKm: 4000 }), point(NOW_MID - DAY, { charges: 1, distanceKm: 40 })] }),
    }),
    check: async (page) => {
      await expect(sentence(page, 'milestone')).toContainText('100 Ladungen.');
      await expect(card(page, 'milestone').getByRole('button', { name: 'Teilen' })).toBeVisible();
      await expect(page.getByTestId('story-confetti')).toHaveCount(1);
    },
  },
  {
    name: 'akku',
    data: () => ({
      ...calmMonth(),
      soh: [
        { id: 'soh-2', carId: null, sohPercent: 92, recordedAt: isoDate(NOW_MID - DAY), createdAt: iso(NOW_MID - DAY), source: 'CHARGE_LOG', sampleSize: 12 },
        { id: 'soh-1', carId: null, sohPercent: 93, recordedAt: isoDate(NOW_MID - 200 * DAY), createdAt: iso(NOW_MID - 200 * DAY), source: 'CHARGE_LOG', sampleSize: 9 },
      ],
    }),
    check: async (page) => {
      await expect(sentence(page, 'battery')).toContainText(/Dein Akku: ~92\s%\. Vor \d+\sMonaten: ~93\s%\./);
      await expect(card(page, 'battery')).toContainText('geschätzt');
    },
  },
  {
    name: 'rangliste',
    keep: ['rank'],
    data: () => ({ ...calmMonth(), ranks: { MONTHLY_DISTANCE: { rank: 2, value: 1234, unit: 'km' }, MONTHLY_KWH: { rank: 3, value: 310, unit: 'kWh' }, MONTHLY_CHARGES: { rank: 7, value: 9, unit: '' } } }),
    check: async (page) => {
      // Abschluss des Vormonats: alle Treppchenplätze in einer Karte, Platz 7 fehlt.
      await expect(sentence(page, 'rank')).toContainText('Im September auf dem Treppchen.');
      await expect(card(page, 'rank')).toContainText(/Platz 2 bei .+ · Platz 3 bei /);
      await expect(card(page, 'rank')).not.toContainText('Platz 7');
      await expect(card(page, 'rank').getByRole('link', { name: 'Rangliste ansehen' })).toHaveAttribute('href', '/leaderboard');
    },
  },
  {
    name: 'stille',
    data: () => ({ ...calmMonth(), logs: [log(NOW_MID, { loggedAt: iso(NOW_MID - 16 * DAY), createdAt: iso(NOW_MID - 16 * DAY), kwhAtVehicle: 28.5 })] }),
    check: async (page) => {
      await expect(sentence(page, 'silence')).toContainText(/Letzte Ladung vor 16\sTagen: 28,5\skWh\./);
      await expect(card(page, 'silence').getByRole('button', { name: 'Alles erfasst' })).toBeVisible();
    },
  },
  {
    name: 'luecke',
    data: () => ({
      ...calmMonth(),
      priceless: [log(NOW_MID, { id: '00000000-0000-0000-0000-0000000c0009', loggedAt: iso(NOW_MID - 2 * DAY), createdAt: iso(NOW_MID - 2 * DAY), kwhAtVehicle: 28.5, costEur: null, priceUnknown: false })],
    }),
    check: async (page) => {
      await expect(sentence(page, 'gap')).toContainText(/Was haben die 28,5\skWh vom 12\.10\. gekostet\?/);
      await expect(card(page, 'gap').getByRole('button', { name: 'Weiß ich nicht mehr' })).toBeVisible();
    },
  },
];

test.describe('Story: Katalog der Hero-Zustände', () => {
  for (const c of CASES) {
    for (const theme of ['light', 'dark'] as const) {
      test(`${c.name} (${theme === 'light' ? 'hell' : 'dunkel'})`, async ({ page }, testInfo) => {
        const errors: string[] = [];
        page.on('pageerror', (err) => errors.push(err.message));
        // Wetterprognose am Ziel kommt direkt von Open-Meteo: feste Antwort statt Netz.
        await page.route('https://api.open-meteo.com/**', route => route.fulfill({ json: {
          daily: { weather_code: [2, 81], temperature_2m_min: [4, 2], temperature_2m_max: [12, 9], wind_gusts_10m_max: [20, 30] },
        } }));
        await openStory(page, c.data(), { theme, keep: c.keep });

        await c.check(page);
        const box = page.getByTestId('story-hero-box');
        const rect = await box.boundingBox();
        expect(rect!.x + rect!.width, 'erste Box ragt nicht über den Rand').toBeLessThanOrEqual(page.viewportSize()!.width);

        await testInfo.attach(`${c.name} ${theme}`, { body: await page.screenshot({ fullPage: true, animations: 'disabled' }), contentType: 'image/png' });
        if (!process.env.CI) {
          // Der erste Bildschirm samt Karte von unten, nicht nur die Box: die Karte liegt fest über der Seite.
          await expect(page).toHaveScreenshot(`${c.name}-${theme}.png`, { animations: 'disabled', maxDiffPixels: 30, threshold: 0.05 });
        }
        expect(errors).toEqual([]);
      });
    }
  }
});

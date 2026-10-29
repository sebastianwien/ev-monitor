import { test, expect, type Page, type Route } from '@playwright/test';
import { calmMonth, openStory, log, trip, NOW_MID, iso } from './fixtures/story';

/**
 * Logbuch auf /logs im neuen Design, auf dem Handy (390 px): Monatskapitel, Tagesblatt, Tipp öffnet das
 * Detail, der Vormonat lädt beim Scrollen nach; ohne Fahrten Ladeseiten mit der Strecke dazwischen.
 * Die Mocks liefern nur ab `from`, wie das Backend, damit das Nachladen echt ist.
 */
test.use({ viewport: { width: 390, height: 844 } });

const at = (month: number, day: number, h: number, m = 0) => new Date(2026, month, day, h, m).getTime();
const id = (n: number) => `00000000-0000-0000-0000-0000000a${String(n).padStart(4, '0')}`;

/** Antwortet wie /logs und /trips: ab `from`, mit `limit` nur die jüngsten */
function windowed(rows: Record<string, unknown>[], dateKey: string) {
  return (route: Route) => {
    const params = new URL(route.request().url()).searchParams;
    const from = params.get('from');
    const limit = params.get('limit');
    let out = [...rows].sort((a, b) => Date.parse(String(b[dateKey])) - Date.parse(String(a[dateKey])));
    if (from) out = out.filter(r => Date.parse(String(r[dateKey])) >= Date.parse(from));
    if (limit) out = out.slice(0, Number(limit));
    return route.fulfill({ json: out });
  };
}

async function openLogbook(page: Page, d: ReturnType<typeof calmMonth>) {
  await openStory(page, d);
  await page.route(/\/api\/trips\?/, windowed(d.trips, 'tripStartedAt'));
  await page.route(/\/api\/logs\?carId=[^&]+&(from=|limit=1$)/, windowed(d.logs, 'loggedAt'));
  // Neu laden: die Übersicht hat die Logs schon vor den Mocks geholt
  await page.goto('/logs');
  await expect(page.getByTestId('logbook')).toBeVisible();
}

test('mit Fahrten: Monat, Tagesblatt, Detail, Vormonat lädt beim Scrollen', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  const d = calmMonth(NOW_MID);
  // Oktober: drei Tage mit je zwei Fahrten; September: eine Fahrt
  d.trips = [12, 10, 7].flatMap((day, i) => [
    trip(NOW_MID, { id: id(i * 2 + 1), tripStartedAt: iso(at(9, day, 8)), tripEndedAt: iso(at(9, day, 8, 30)), distanceKm: 15, socStart: 80, socEnd: 76 }),
    trip(NOW_MID, { id: id(i * 2 + 2), tripStartedAt: iso(at(9, day, 17)), tripEndedAt: iso(at(9, day, 17, 30)), distanceKm: 15, socStart: 76, socEnd: 72 }),
  ]).concat([trip(NOW_MID, { id: id(99), tripStartedAt: iso(at(8, 20, 9)), tripEndedAt: iso(at(8, 20, 9, 40)), distanceKm: 30, socStart: 90, socEnd: 82 })]);
  d.logs = [];
  await openLogbook(page, d);

  const book = page.getByTestId('logbook');
  const october = book.getByTestId('logbook-month').first();
  await expect(october.getByTestId('logbook-month-head')).toContainText('Oktober 2026');
  await expect(october.getByTestId('logbook-month-head')).toContainText('6 Fahrten');
  await expect(october.getByTestId('logbook-day')).toHaveCount(3);
  // Zwischen den Tagen ohne Eintrag je eine Zeile, keine Liste leerer Tage
  await expect(october.getByTestId('logbook-idle').first()).toContainText('keine Fahrt');

  // Tipp auf eine Fahrt öffnet das Detail als Sheet
  await book.getByTestId('day-row-trip').first().click();
  await expect(page.getByTestId('logbook-detail')).toBeVisible();
  await expect(page.getByTestId('day-trip-panel')).toBeVisible();
  await page.getByTestId('day-trip-panel').getByRole('button').first().click();
  await expect(page.getByTestId('logbook-detail')).toBeHidden();

  // Ans Ende scrollen: der September kommt ohne Klick
  await page.getByTestId('logbook-older').scrollIntoViewIfNeeded();
  await expect(book.getByTestId('logbook-month-head').filter({ hasText: 'September 2026' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('ohne Fahrten: Ladeseiten mit Strecke, Rhythmus und fehlendem Preis', async ({ page }) => {
  const d = calmMonth(NOW_MID);
  d.trips = [];
  d.logs = [
    log(NOW_MID, { id: id(1), loggedAt: iso(at(9, 13, 18)), costEur: 11.89, kwhCharged: 34.8, odometerKm: 24812, distanceSinceLastChargeKm: 318, consumptionKwhPer100km: 16.9 }),
    log(NOW_MID, { id: id(2), loggedAt: iso(at(9, 8, 12)), costEur: null, kwhCharged: 39.6, odometerKm: 24494, distanceSinceLastChargeKm: 486, consumptionKwhPer100km: 19.4 }),
    log(NOW_MID, { id: id(3), loggedAt: iso(at(9, 4, 19)), costEur: 9.02, kwhCharged: 30.2, odometerKm: 24008 }),
  ];
  await openLogbook(page, d);

  const book = page.getByTestId('logbook');
  await expect(book.getByTestId('chargebook-row')).toHaveCount(3);
  const stretches = book.getByTestId('chargebook-stretch');
  await expect(stretches).toHaveCount(2);
  await expect(stretches.first()).toContainText('318 km gefahren');
  await expect(stretches.first()).toContainText('5 Tage');
  await expect(book.getByTestId('chargebook-rhythm')).toContainText('Du lädst etwa alle');
  await expect(book.getByTestId('chargebook-amend')).toHaveCount(1);
});

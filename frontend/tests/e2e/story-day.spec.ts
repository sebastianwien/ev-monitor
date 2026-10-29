import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, log, trip, NOW_MID, iso } from './fixtures/story';

/**
 * Tagesblatt: Tag im Zeitstrahl antippen. Gleicher Ablauf bei 390, 820 und 1440 px; Spracheingabe
 * im Ladungs-Formular nur unter 1024 px. Lesen und Ändern laufen über Mocks.
 */
const day = (h: number, m = 0) => new Date(2026, 9, 12, h, m).getTime();
const T1 = '00000000-0000-0000-0000-0000000d0001';
const T2 = '00000000-0000-0000-0000-0000000d0002';
const L1 = '00000000-0000-0000-0000-0000000d0101';

function data() {
  const d = calmMonth(NOW_MID);
  d.trips = [
    trip(NOW_MID, { id: T1, tripStartedAt: iso(day(9)), tripEndedAt: iso(day(9, 20)), distanceKm: 12, socStart: 80, socEnd: 76,
      tracePolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@', locationStartGeohash: 'u33df5', locationEndGeohash: 'u33dc0' }),
    trip(NOW_MID, { id: T2, tripStartedAt: iso(day(9, 30)), tripEndedAt: iso(day(9, 50)), distanceKm: 14, socStart: 76, socEnd: 71,
      locationStartGeohash: 'u33dc0', locationEndGeohash: 'u33d9z' }),
  ];
  d.logs = [log(NOW_MID, {
    id: L1, loggedAt: iso(day(13)), chargeDurationMinutes: 35, isPublicCharging: true, cpoName: 'Kaufland',
    chargingType: 'DC', costEur: null, socBeforeChargePercent: 30, socAfterChargePercent: 80,
  })];
  return d;
}

async function mockDay(page: Page, d: ReturnType<typeof data>) {
  const calls: string[] = [];
  await page.route(/\/api\/logs\?carId=[^&]+&from=/, route => route.fulfill({ json: d.logs }));
  await page.route(/\/api\/logs\/voice-quota/, route => route.fulfill({ json: { plan: 'admin', limit: null, remaining: null, exhausted: false, resetsOn: '2026-11-01' } }));
  await page.route(/\/api\/trips\/[^/?]+(\/merge|\/restore)?$/, route => {
    const r = route.request();
    calls.push(`${r.method()} ${new URL(r.url()).pathname}`);
    if (r.method() === 'DELETE' || r.url().endsWith('/restore')) return route.fulfill({ status: 204, body: '' });
    return route.fulfill({ json: d.trips[0] });
  });
  return calls;
}

async function openDay12(page: Page) {
  const bar = page.getByTestId('story-days');
  const box = (await bar.boundingBox())!;
  await page.mouse.click(box.x + box.width * (11.5 / 31), box.y + box.height - 4);
  await expect(page.getByTestId('story-day-sheet')).toBeVisible();
  await expect(page.getByTestId('day-title')).toContainText('12');
}

for (const [width, height, voice] of [[390, 844, true], [820, 1180, true], [1440, 900, false]] as const) {
  test.describe(`Tagesblatt ${width} px`, () => {
    test.use({ viewport: { width, height } });

    test('Ablauf, Ladung öffnen, Spracheingabe, Zusammenführen, Löschen mit Rückgängig', async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', e => errors.push(e.message));
      const d = data();
      await openStory(page, d);
      const calls = await mockDay(page, d);
      await openDay12(page);

      // Ablauf: zwei Fahrten, eine Ladung mit fehlendem Preis
      await expect(page.getByTestId('day-row-trip')).toHaveCount(2);
      await expect(page.getByTestId('day-row-charge')).toContainText('Kaufland');
      await expect(page.getByTestId('day-missing')).toBeVisible();
      // Akku-Balken wie am Handy, Minimap je Fahrt (mit Spur durchgezogen, ohne Spur Luftlinie)
      await expect(page.locator('[data-testid="day-bar"][data-kind="charge"]').first()).toBeVisible();
      await expect(page.getByTestId('day-ribbon-caption')).toContainText('30 %');
      await expect(page.getByTestId('day-trip-thumb')).toHaveCount(2);

      // Ladung öffnen: Spracheingabe nur unter 1024 px
      await page.getByTestId('day-row-charge').click();
      await expect(page.getByTestId('day-detail')).toBeVisible();
      await expect(page.getByTestId('day-detail').getByText(/Ladung|Ladevorgang/).first()).toBeVisible();
      await expect(page.getByTestId('day-detail').getByTestId('voice-capture')).toHaveCount(voice ? 1 : 0);
      await page.getByTestId('edit-back').click();
      await expect(page.getByTestId('day-detail')).toHaveCount(0);

      // Zusammenführen: zweistufig
      await page.getByTestId('day-merge').click();
      await page.getByTestId('day-merge-confirm').click();
      await expect.poll(() => calls).toContain(`POST /api/trips/${T1}/merge`);

      // Fahrt öffnen: große Karte oben, Formular in der neuen CI
      await page.getByTestId('day-row-trip').first().click();
      await expect(page.getByTestId('day-trip-map')).toBeVisible();
      // Die Karte bleibt in ihrem Rahmen, statt das ganze Blatt zu füllen
      const map = (await page.getByTestId('day-trip-map').locator('.activity-map').boundingBox())!;
      expect(map.height).toBeLessThanOrEqual(300);
      await expect(page.getByTestId('day-trip-form')).toBeVisible();
      // Speichern sitzt in der Fußleiste außerhalb des Formulars und sendet es trotzdem ab
      await expect(page.getByTestId('trip-start')).toHaveValue('09:00');
      await page.getByTestId('day-trip-save').click();
      await expect.poll(() => calls).toContain(`PATCH /api/trips/${T1}`);
      await page.getByTestId('day-row-trip').first().click();

      // Fahrt löschen, dann Rückgängig im Toast
      await page.getByTestId('day-trip-delete').click();
      await page.getByTestId('day-trip-delete-confirm').click();
      await expect.poll(() => calls).toContain(`DELETE /api/trips/${T1}`);
      await page.getByRole('button', { name: /Rückgängig|Undo/ }).click();
      await expect.poll(() => calls).toContain(`POST /api/trips/${T1}/restore`);

      // Zurück-Taste schließt das Blatt, die Seite bleibt
      await page.goBack();
      await expect(page.getByTestId('story-day-sheet')).toHaveCount(0);
      await expect(page).toHaveURL(/\/story/);
      expect(errors).toEqual([]);
    });
  });
}

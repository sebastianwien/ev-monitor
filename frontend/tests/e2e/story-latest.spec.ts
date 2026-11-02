import { test, expect } from '@playwright/test';
import { calmMonth, openStory, log, trip, DAY, HOUR, iso } from './fixtures/story';

/**
 * "Zuletzt" (letzte Ladung, letzte Fahrt) auf 390 px: lange Texte kürzen sich, statt die Zeilen
 * über den Bildschirm zu schieben. Auch während einer Live-Ladung, wo der Live-Hero darüber steht.
 */
test.use({ viewport: { width: 390, height: 844 } });

test('lange Betreibernamen schieben "Zuletzt" nicht über den Bildschirm', async ({ page }) => {
  const d = calmMonth();
  d.logs = [log(d.now, {
    loggedAt: iso(d.now - 2 * DAY), createdAt: iso(d.now - 2 * DAY), isPublicCharging: true, chargingType: 'DC',
    cpoName: 'Stadtwerke Beispielstadt Energie und Mobilität Ladeinfrastruktur GmbH',
  })];
  d.trips = [trip(d.now, { tripStartedAt: iso(d.now - 3 * HOUR), tripEndedAt: iso(d.now - 2 * HOUR), distanceKm: 123.4 })];
  const now = iso(d.now);
  d.live = {
    isActive: true, chargingType: 'AC', powerKw: 11, socPercent: 54, energyRemainingKwh: 20, timeToFullMinutes: 95,
    estRangeKm: 260, chargeAmps: 16, sessionStartedAt: now, sessionEndedAt: null, socAtSessionStart: 30, chargeLimitSoc: 80, lastUpdatedAt: now,
  };
  await openStory(page, d);

  await expect(page.getByTestId('story-live-hero')).toBeVisible();
  const rows = page.getByTestId('story-latest').getByRole('button');
  await expect(rows).toHaveCount(2);
  const width = page.viewportSize()!.width;
  for (const box of await Promise.all((await rows.all()).map(r => r.boundingBox()))) {
    expect(box!.x + box!.width).toBeLessThanOrEqual(width);
  }
});

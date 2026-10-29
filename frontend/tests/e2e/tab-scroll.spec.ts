import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, trip, NOW_MID, iso } from './fixtures/story';

/**
 * Übersicht und Logs liegen mobil in einer Wisch-Schiene. Jeder Tab behält seinen eigenen Scrollstand:
 * Scrollen im einen verschiebt den anderen nicht, zurück steht man wieder dort, wo man war.
 */
test.use({ viewport: { width: 390, height: 844 } });

function longMonth() {
  const d = calmMonth(NOW_MID);
  const at = (day: number, h: number) => new Date(2026, 9, day, h).getTime();
  d.trips = Array.from({ length: 12 }, (_, i) => trip(NOW_MID, {
    id: `00000000-0000-0000-0000-0000000e${String(i).padStart(4, '0')}`,
    tripStartedAt: iso(at(13 - i, 8)), tripEndedAt: iso(at(13 - i, 9)), distanceKm: 20, socStart: 80, socEnd: 70,
  }));
  return d;
}
const scrollY = (page: Page) => page.evaluate(() => Math.round(window.scrollY));

test('jeder Tab behält seinen Scrollstand', async ({ page }) => {
  const d = longMonth();
  await openStory(page, d);
  await page.route(/\/api\/logs\?carId=[^&]+&(from=|limit=1$)/, route => route.fulfill({ json: d.logs }));

  await page.getByRole('link', { name: 'Logs' }).first().click();
  await expect(page.getByTestId('logbook-month').first()).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 600));
  await expect.poll(() => scrollY(page)).toBe(600);

  await page.getByRole('link', { name: 'Start' }).first().click();
  await expect(page).toHaveURL(/\/story/);
  await expect.poll(() => scrollY(page)).toBe(0);

  await page.getByRole('link', { name: 'Logs' }).first().click();
  await expect(page).toHaveURL(/\/logs/);
  await expect.poll(() => scrollY(page)).toBe(600);
});

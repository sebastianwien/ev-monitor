import { test, expect } from '@playwright/test';
import { calmMonth, openStory, point, stats, NOW_MONTH_START } from './fixtures/story';

/**
 * /story ab 1024 px: Details und Neuigkeiten stehen in der rechten Spalte, nichts kommt von unten
 * und nichts dunkelt ab. Unter 1024 px bleibt alles beim Sheet und beim Stapel (story-moments).
 */
test.use({ viewport: { width: 1440, height: 900 } });

const review = () => ({
  ...calmMonth(NOW_MONTH_START),
  monthStats: stats({ totalCharges: 1, totalDistanceKm: 40, energyCostEur: 5, totalCostEur: 5, totalKwhCharged: 9, chargesOverTime: [point(new Date(2026, 9, 1, 12).getTime())] }),
  previousStats: stats({ totalDistanceKm: 512, energyCostEur: 61, totalCostEur: 61, totalCharges: 11, totalKwhCharged: 95 }),
});

test('Kachel öffnet ihr Detail rechts, Pfeiltaste blättert, Escape führt zurück zum Monat', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (err) => errors.push(err.message));
  await openStory(page, calmMonth());

  const side = page.getByTestId('story-side');
  await expect(side.getByTestId('story-feed')).toBeVisible();
  const tiles = page.getByTestId('story-tiles').getByRole('button');
  const first = tiles.first();
  await first.click();

  await expect(side.getByTestId('story-pane')).toBeVisible();
  await expect(page.getByTestId('story-sheet')).toHaveCount(0);
  await expect(page.locator('[aria-modal="true"]')).toHaveCount(0);
  await expect(first).toHaveAttribute('aria-current', 'true');
  // Die Seite bleibt bedienbar: Kacheln und Detail gleichzeitig sichtbar
  await expect(tiles.nth(1)).toBeVisible();

  await page.keyboard.press('ArrowRight');
  await expect(tiles.nth(1)).toHaveAttribute('aria-current', 'true');
  await expect(first).not.toHaveAttribute('aria-current', 'true');

  await page.keyboard.press('Escape');
  await expect(side.getByTestId('story-pane')).toHaveCount(0);
  await expect(side.getByTestId('story-feed')).toBeVisible();
  await expect(tiles.nth(1)).toBeFocused();
  expect(errors).toEqual([]);
});

test('Spalte bleibt beim Scrollen stehen', async ({ page }) => {
  // Niedriges Fenster, damit die Seite sicher weiter scrollt, als die Spalte von oben entfernt ist
  await page.setViewportSize({ width: 1440, height: 480 });
  await openStory(page, calmMonth());
  const side = page.getByTestId('story-side');
  const before = (await side.boundingBox())!.y;
  await page.mouse.wheel(0, 400);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before);
  // Ohne Sticky läge die Spalte jetzt über dem oberen Rand (y < 0), mit Sticky steht sie unter dem Kopf
  expect((await side.boundingBox())!.y).toBeGreaterThan(0);
});

test('Neuigkeit steht oben in der Spalte, ohne Abdunkeln, × nimmt sie weg', async ({ page }) => {
  await openStory(page, review(), { keep: ['month_change'] });

  const inline = page.getByTestId('story-side').getByTestId('story-news-inline');
  await expect(inline.getByTestId('story-news-month_change')).toBeVisible();
  await expect(page.getByTestId('story-news')).toHaveCount(0);
  await expect(page.getByTestId('story-news-backdrop')).toHaveCount(0);
  await expect(page.getByTestId('story-news-tab')).toHaveCount(0);

  await inline.getByTestId('story-news-dismiss').click();
  await expect(page.getByTestId('story-news-month_change')).toHaveCount(0);
});

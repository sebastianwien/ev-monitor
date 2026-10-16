import { test, expect, type Page } from '@playwright/test';

// Modell-Rangliste (Release 2: Default unter /modelle). Die alte Liste liegt hinter ?ansicht=alt.
const URL = '/modelle';

const rows = (page: Page) => page.locator('ol.mr-list > li');
const rowToggle = (page: Page, i: number) => rows(page).nth(i).locator('button[aria-expanded]');

async function openRanking(page: Page) {
  await page.goto(URL);
  await expect(rows(page).first()).toBeVisible({ timeout: 15_000 });
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
  test.describe(`Modell-Rangliste V2 (${viewport.width} px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport);
    });

    test('zeigt Zeilen und ist als Default indexierbar', async ({ page }) => {
      await openRanking(page);
      expect(await rows(page).count()).toBeGreaterThan(1);
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /^index/);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/modelle$/);
    });

    test('Sortierung "Größte Reichweite" ändert die erste Zeile', async ({ page }) => {
      await openRanking(page);
      const firstBefore = await rows(page).first().getByTestId('row-name').innerText();
      await page.getByRole('button', { name: 'Größte Reichweite' }).click();
      await expect(page.getByRole('button', { name: 'Größte Reichweite' })).toHaveAttribute('aria-pressed', 'true');
      await expect.poll(() => rows(page).first().getByTestId('row-name').innerText()).not.toBe(firstBefore);
    });

    test('Zeile aufklappen zeigt die Fahrerwerte und den Link zur Modellseite', async ({ page }) => {
      await openRanking(page);
      const toggle = rowToggle(page, 0);
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await toggle.click();
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');

      const detail = page.locator(`#${await toggle.getAttribute('aria-controls')}`);
      await expect(detail.getByTestId('detail-measured')).toContainText(/Verbrauch \d+,\d.*Ladevorg/);
      await expect(detail.getByRole('link', { name: /^Modellseite / })).toBeVisible();
    });

    test('zwei Modelle wählen und vergleichen', async ({ page }) => {
      await openRanking(page);
      for (const i of [0, 1]) {
        if (viewport.width >= 1024) {
          // desktop: at the end of the row
          await rows(page).nth(i).getByTestId('compare-row').click();
        } else {
          // phone: in the open row
          await rowToggle(page, i).click();
          await rows(page).nth(i).getByTestId('compare-open').click();
        }
      }
      await page.getByRole('button', { name: 'Vergleichen' }).click();
      await expect(page).toHaveURL(/\/modelle\/vergleich\?models=[^,]+,[^,]+/);
    });

    test('kein horizontales Scrollen', async ({ page }) => {
      await openRanking(page);
      // Gegen clientWidth statt Viewport-Breite: die App reserviert einen Scrollbar-Gutter.
      // Chip-Zeilen scrollen in sich selbst und zählen nicht.
      const { scrollWidth, clientWidth } = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
    });
  });
}

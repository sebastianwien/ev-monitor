import { test, expect, type Page } from '@playwright/test';

// Neue Modell-Rangliste, Vorschau hinter ?ansicht=neu (Release 1). V1 bleibt Default.
const URL = '/modelle?ansicht=neu';

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

    test('zeigt Zeilen und ist in der Vorschau noindex', async ({ page }) => {
      await openRanking(page);
      expect(await rows(page).count()).toBeGreaterThan(1);
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
    });

    test('Sortierung "Größte Reichweite" ändert die erste Zeile', async ({ page }) => {
      await openRanking(page);
      const firstBefore = await rows(page).first().locator('.mr-who').innerText();
      await page.getByRole('button', { name: 'Größte Reichweite' }).click();
      await expect(page.getByRole('button', { name: 'Größte Reichweite' })).toHaveAttribute('aria-pressed', 'true');
      await expect.poll(() => rows(page).first().locator('.mr-who').innerText()).not.toBe(firstBefore);
    });

    test('Zeile aufklappen zeigt Jahreszeiten und Link zur Modellseite', async ({ page }) => {
      await openRanking(page);
      const toggle = rowToggle(page, 0);
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await toggle.click();
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');

      const detail = page.locator(`#${await toggle.getAttribute('aria-controls')}`);
      await expect(detail.getByRole('group', { name: /Verbrauch nach Jahreszeit/ })).toBeVisible();
      await expect(detail.locator('a[href*="/modelle/"]')).toBeVisible();
    });

    test('zwei Modelle wählen und vergleichen', async ({ page }) => {
      await openRanking(page);
      for (const i of [0, 1]) {
        if (viewport.width >= 1024) {
          await rows(page).nth(i).getByRole('button', { name: /zum Vergleich hinzufügen/ }).click();
        } else {
          await rowToggle(page, i).click();
          await rows(page).nth(i).getByRole('button', { name: 'Zum Vergleich' }).click();
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

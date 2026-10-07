import { test, expect, type Page } from '@playwright/test';

// Kaufhilfe auf der Modell-Rangliste (Vorschau ?ansicht=neu): Datenlage, Modelle ohne
// Fahrerdaten (Phase A) und der Bedarfs-Check im Hero (Phase B).
const URL = '/modelle?ansicht=neu';

const rows = (page: Page) => page.locator('ol.mr-list > li');

async function openRanking(page: Page) {
  await page.goto(URL);
  await expect(rows(page).first()).toBeVisible({ timeout: 15_000 });
}

/** Ersetzt den Wert eines Eingabefelds komplett, damit der input-Handler mit dem neuen Wert läuft. */
async function setDistance(page: Page, testId: string, value: string) {
  const input = page.getByTestId(testId);
  await input.click();
  await input.fill(value);
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
  test.describe(`Kaufhilfe (${viewport.width} px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport);
    });

    test('Datenlage: jede Zeile nennt Ladevorgänge und Fahrer', async ({ page }) => {
      await openRanking(page);
      const first = rows(page).first();
      await expect(first.locator(viewport.width >= 1024 ? '.mr-hint' : '.mr-who')).toContainText(/Ladevorg[aä]ng.*, \d+ Fahrer/);
      if (viewport.width >= 1024) {
        await expect(first.getByTestId('savings-column')).toContainText(/^[−+]\d/);
      }
    });

    test('Bedarfs-Check zeigt mit den Vorgaben sofort einen Satz', async ({ page }) => {
      await openRanking(page);
      await expect(page.getByTestId('needs-daily')).toHaveValue('40');
      await expect(page.getByTestId('needs-longest')).toHaveValue('400');
      await expect(page.getByTestId('needs-summary')).toContainText(/Bei 40 km am Tag musst du mit \d+ von \d+ Modellen/);
    });

    test('Tagesstrecke ändern ändert Satz und Hinweis der ersten Zeile', async ({ page }) => {
      await openRanking(page);
      const summary = page.getByTestId('needs-summary');
      const firstHint = rows(page).first().getByTestId('needs-hint');
      const summaryBefore = await summary.innerText();
      const hintBefore = await firstHint.innerText();

      await setDistance(page, 'needs-daily', '120');

      await expect(summary).toContainText('Bei 120 km am Tag');
      expect(await summary.innerText()).not.toBe(summaryBefore);
      await expect.poll(() => firstHint.innerText()).not.toBe(hintBefore);
    });

    test('Chip "Schafft meine längste Fahrt ohne Stopp" filtert die Liste', async ({ page }) => {
      await openRanking(page);
      // 100 km schafft jedes bewertbare Modell, 5000 km keins
      await setDistance(page, 'needs-longest', '5000');
      await page.getByTestId('trip-chip').click();
      await expect(page.getByTestId('trip-chip')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId('ranking-empty')).toBeVisible();

      await setDistance(page, 'needs-longest', '100');
      await expect(rows(page).first()).toBeVisible();
      await expect(rows(page).first().getByTestId('needs-hint')).toContainText('ohne Ladestopp');
    });

    test('Modelle ohne Fahrerdaten: eingeklappt, Suche klappt auf und erklärt den Leerzustand', async ({ page, request }) => {
      const withoutData = await (await request.get('/api/public/models/without-data')).json() as { modelDisplayName: string }[];
      test.skip(withoutData.length === 0, 'lokal hat jedes Modell mit WLTP-Spec schon Fahrerdaten');

      await openRanking(page);
      const details = page.getByTestId('without-data');
      await expect(details).toBeVisible();
      await expect(details).not.toHaveAttribute('open', '');

      await page.getByRole('searchbox').fill(withoutData[0].modelDisplayName);
      await expect(details).toHaveAttribute('open', '');
      await expect(page.getByTestId('ranking-empty')).toContainText('noch keine Fahrerdaten');
    });

    test('Annahmen: Sheet öffnet, Escape schließt, Fokus kehrt zum Chip zurück', async ({ page }) => {
      await openRanking(page);
      const chip = page.getByTestId('assumptions-chip');
      await chip.click();
      const sheet = page.getByTestId('assumptions-sheet');
      await expect(sheet).toBeVisible();
      await expect(sheet.getByRole('button', { name: 'Schließen' }).first()).toBeFocused();
      await expect(sheet).toContainText('bleiben in deinem Browser');

      await page.keyboard.press('Escape');
      await expect(sheet).toBeHidden();
      await expect(chip).toBeFocused();
    });

    test('Kosten gegen Verbrenner stehen in der Zeile, Kostenmodus macht Euro zur großen Zahl', async ({ page }) => {
      await openRanking(page);
      const first = rows(page).first();
      // DE: Kraftstoffpreis ist vorbelegt, der Vergleich steht sofort
      await expect(first.getByTestId('savings')).toContainText(/(unter|über) Benziner/);

      await page.getByTestId('assumptions-chip').click();
      await page.getByTestId('main-value-cost').click();
      await page.keyboard.press('Escape');
      if (viewport.width < 1024) {
        await expect(first.locator('.mr-val')).toContainText('€ pro 100 km');
      }

      // Ohne Kraftstoffpreis kein Vergleich
      await page.getByTestId('assumptions-chip').click();
      await page.getByTestId('assumption-fuel-price').fill('');
      await page.getByTestId('assumption-fuel-price').press('Tab');
      await page.keyboard.press('Escape');
      await expect(first.getByTestId('savings')).toHaveCount(0);
    });

    test('Priorität "Meiste Daten" sortiert um und bleibt nach Neuladen', async ({ page }) => {
      await openRanking(page);
      const firstBefore = await rows(page).first().locator('.mr-who').innerText();
      await page.getByTestId('priority-data').click();
      await expect(page.getByRole('button', { name: 'Meiste Daten' })).toHaveAttribute('aria-pressed', 'true');
      await expect.poll(() => rows(page).first().locator('.mr-who').innerText()).not.toBe(firstBefore);

      await page.reload();
      await expect(rows(page).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('priority-data')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByRole('button', { name: 'Meiste Daten' })).toHaveAttribute('aria-pressed', 'true');
    });
  });
}

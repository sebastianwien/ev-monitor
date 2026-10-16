import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

// Kaufhilfe auf der Modell-Rangliste (/modelle, seit Release 2 Default): Datenlage, Modelle ohne
// Fahrerdaten (Phase A) und der Bedarfs-Check im Hero (Phase B).
const URL = '/modelle';

const rows = (page: Page) => page.locator('ol.mr-list > li');

async function openRanking(page: Page) {
  await page.goto(URL);
  await expect(rows(page).first()).toBeVisible({ timeout: 15_000 });
}

async function login(page: Page) {
  // the feature announcement would cover the page after login
  await page.addInitScript((seen: string[]) => { localStorage.setItem('seen-announcements', JSON.stringify(seen)); }, featureAnnouncements.map(a => a.key));
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

/** Pendelweg und Tage: die zwei Antworten, die die Pendelzeile braucht (Laden zuhause zählt bis zur Antwort) */
async function answerCommute(page: Page, km: number, days: number) {
  await setSlider(page, 'commute', km);
  await setSlider(page, 'commuteDays', days);
}

/** Setzt einen Regler der Karte und löst input plus change aus, wie ein losgelassener Daumen */
async function setSlider(page: Page, key: string, value: number) {
  await page.getByTestId(`slider-needs-${key}`).evaluate((el, v) => {
    const input = el as HTMLInputElement;
    input.value = String(v);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }, value);
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
  test.describe(`Kaufhilfe (${viewport.width} px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport);
    });

    test('Datenlage: jede Zeile nennt Ladevorgänge und Fahrer', async ({ page }) => {
      await openRanking(page);
      const first = rows(page).first();
      await expect(first.locator('.mr-meta')).toContainText(/Ladevorg[aä]ng.*, \d+ Fahrer/);
      if (viewport.width >= 1024) {
        // cost cell: electricity plus the combustion car of the class
        await expect(first.getByTestId('cost-cell')).toContainText(/Strom\s*\d+,\d\d €\s*Benziner \d+,\d\d €/);
        // litres follow the vehicle class, so the combustion figure differs between classes
        const captions = await rows(page).getByTestId('cost-cell').allTextContents();
        const amounts = new Set(captions.map(c => c.match(/Benziner (\d+,\d\d) €/)?.[1]).filter(Boolean));
        expect(amounts.size).toBeGreaterThan(1);
      }
      // bar pair: manufacturer figure against the drivers' figure, with the deviation
      await expect(first.getByTestId('consumption-bars')).toContainText(/Hersteller\s*\d+,\d.*Fahrer\s*\d+,\d\s*[+−]\d+ %/);
      if (viewport.width >= 1280) {
        // range cell: one-stop range from the community DC power, or the honest gap
        await expect(first.getByTestId('range-cell')).toContainText(/\d+ km\s*Autobahn (bis )?\d+\s*1 Stopp (bis )?\d+\s*2 Stopps (bis )?\d+/);
      }
      if (viewport.width >= 1024) {
      }
    });

    test('Kapitel 1 startet leer: Reichweite als Spanne, sonst nichts erfunden, Zeilen ohne Zahlen', async ({ page }) => {
      await openRanking(page);
      await expect(page.getByTestId('ranking-card')).toBeVisible();
      await expect(page.getByTestId('slider-needs-commute-value')).toHaveText('noch offen');
      await expect(page.getByTestId('ranking-answer-range')).toContainText(/\d+ km bis \d+ km/);
      await expect(page.getByTestId('ranking-answer-stops')).toHaveCount(0);
      await expect(page.getByTestId('ranking-answer-cost')).toHaveCount(0);
      await expect(rows(page).first().getByTestId('needs-hint')).toHaveCount(0);
    });

    test('Gast: Spannen über alle Modelle frei, keine Zahl je Modell, ein Gate', async ({ page }) => {
      await openRanking(page);
      await answerCommute(page, 40, 5);
      await setSlider(page, 'longTrip', 600);
      await expect(page.getByTestId('ranking-answer-stops')).toContainText('600 km');
      await expect(page.getByTestId('ranking-answer-charge')).toContainText(/alle \d+/);
      await expect(page.getByTestId('ranking-answer-cost')).toContainText(/zu Hause geladen für \d+,\d ct\/kWh/);
      await expect(page.getByTestId('ranking-gate-lead')).toContainText(/Modelle schaffen deine 600 km/);
      await expect(page.getByTestId('top-five')).toHaveCount(0);
      await expect(page.getByTestId('personal-range-tune')).toHaveCount(0);

      // the open row points to the one gate in the card
      await rows(page).first().locator('button[aria-expanded]').click();
      await page.getByTestId('row-unlock').click();
      await expect(page.getByTestId('ranking-gate')).toBeInViewport();
      await expect(page.getByTestId('ranking-gate-open')).toHaveCount(0);
    });

    test('eingeloggt: Top 5 als Tabelle, Wichtigkeit sortiert um, Wallbox ändert die Kosten', async ({ page }) => {
      await login(page);
      await openRanking(page);
      await expect(page.getByTestId('ranking-gate')).toHaveCount(0);
      await answerCommute(page, 40, 5);
      await setSlider(page, 'longTrip', 600);
      await setSlider(page, 'annualKm', 15000);
      await expect(page.getByTestId('top-five-row')).toHaveCount(5);
      const names = () => page.getByTestId('top-five-row').locator('a').allTextContents();
      const far = await names();

      await page.getByTestId('priority-cost').click();
      await expect.poll(names).not.toEqual(far);
      await expect(page.getByTestId('top-five-row').first().locator('[role="cell"]').first()).toHaveAttribute('data-testid', 'top-five-month');

      const cost = page.getByTestId('ranking-answer-cost');
      await expect(cost).toContainText('zu Hause geladen');
      const before = await cost.innerText();
      await page.getByTestId('wallbox-no').click();
      await expect(cost).toContainText('unterwegs geladen');
      await expect.poll(() => cost.innerText()).not.toBe(before);
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
      await expect(first.getByTestId('savings')).toContainText(/\(Benziner \d+,\d\d\)/);

      await page.getByTestId('assumptions-chip').click();
      await page.getByTestId('main-value-cost').click();
      await page.keyboard.press('Escape');
      if (viewport.width < 1024) {
        await expect(first.locator('.mr-val')).toContainText('€ pro 100 km');
      }

      // Ein eingetragener Literwert gilt für alle Klassen, leer heißt wieder je Klasse
      await page.getByTestId('assumptions-chip').click();
      await expect(page.getByTestId('assumption-liters')).toHaveValue('');
      await expect(page.getByTestId('assumption-liters-hint')).toContainText(/Kompakt 6,8/);
      await page.getByTestId('assumption-liters').fill('9');
      await page.getByTestId('assumption-liters').press('Tab');
      await expect(page.getByTestId('assumption-liters-hint')).toContainText('alle Fahrzeugklassen');
      await page.keyboard.press('Escape');
      if (viewport.width >= 1024) {
        const captions = await rows(page).getByTestId('cost-cell').allTextContents();
        // 9 l for every class: one combustion figure in every row that has one
        expect(new Set(captions.map(c => c.match(/Benziner (\d+,\d\d) €/)?.[1]).filter(Boolean)).size).toBe(1);
      }
      await page.getByTestId('assumptions-chip').click();
      await page.getByTestId('assumption-liters').fill('');
      await page.getByTestId('assumption-liters').press('Tab');
      await expect(page.getByTestId('assumption-liters-hint')).toContainText(/Kompakt 6,8/);

      // Ohne Kraftstoffpreis kein Vergleich
      await page.getByTestId('assumption-fuel-price').fill('');
      await page.getByTestId('assumption-fuel-price').press('Tab');
      await page.keyboard.press('Escape');
      await expect(first.getByTestId('savings')).not.toContainText('Benziner');
    });

    test('Sortierung "Meiste Daten" sortiert um und bleibt nach Neuladen', async ({ page }) => {
      await openRanking(page);
      const firstBefore = await rows(page).first().locator('.mr-who').innerText();
      await page.getByRole('button', { name: 'Meiste Daten' }).click();
      await expect(page.getByRole('button', { name: 'Meiste Daten' })).toHaveAttribute('aria-pressed', 'true');
      await expect.poll(() => rows(page).first().locator('.mr-who').innerText()).not.toBe(firstBefore);

      await page.reload();
      await expect(rows(page).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Meiste Daten' })).toHaveAttribute('aria-pressed', 'true');
    });

    test('Einstiegskarten springen in Kapitel 1 und in die Liste', async ({ page }) => {
      await openRanking(page);
      await page.getByTestId('entry-fit').click();
      await expect(page.getByTestId('ranking-card')).toBeInViewport();
      await page.getByTestId('entry-lean').click();
      await expect(page.locator('section.mr-board')).toBeInViewport();
    });
  });
}

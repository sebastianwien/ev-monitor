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
        await expect(first.getByTestId('range-cell')).toContainText(/\d+ km\s*1 Stopp (bis )?\d+\s*2 Stopps (bis )?\d+/);
      }
      if (viewport.width >= 1024) {
      }
    });

    test('Bedarfs-Check startet leer, die längste Fahrt allein bringt die Stopp-Kacheln', async ({ page }) => {
      await openRanking(page);
      await expect(page.getByTestId('slider-needs-commute-value')).toHaveText('noch offen');
      await expect(page.getByTestId('slider-needs-longTrip-value')).toHaveText('noch offen');
      await expect(page.getByTestId('needs-prompt')).toBeVisible();
      await expect(rows(page).first().getByTestId('needs-hint')).toHaveCount(0);
      await expect(page.getByTestId('needs-stops-0')).toHaveCount(0);

      await setSlider(page, 'longTrip', 400);
      await expect(page.getByTestId('needs-trip-title')).toContainText('So viele Modelle schaffen deine 400 km');
      await expect(page.getByTestId('needs-stops-0')).toBeVisible();
      // nothing narrows the list yet, so no count line and no repeated basis under the chips
      await expect(page.getByTestId('ranking-count')).toHaveCount(0);
      await expect(page.getByTestId('needs-change')).toHaveCount(0);

      if (viewport.width >= 1280) {
        await expect(rows(page).first().getByTestId('range-cell')).toContainText(/\d+ km\s*1 Stopp (bis )?\d+\s*2 Stopps (bis )?\d+/);
      }
    });

    test('ohne Login: Kacheln frei, Zeilen ohne Zahlen, Gate in der Karte', async ({ page }) => {
      await openRanking(page);
      const firstHint = rows(page).first().getByTestId('needs-hint');
      await expect(firstHint).toHaveCount(0);

      await setSlider(page, 'longTrip', 250);
      await expect(firstHint).toContainText(/Ladestopp|nicht bewertbar/);
      await expect(firstHint).not.toContainText(/Tage|Wochen|Stopps/);
      await expect(page.getByTestId('needs-trip-title')).toContainText('250 km');
      await expect(firstHint).toContainText('250 km');
      // the commute figure waits behind the unlock, in the card and in each row
      await expect(page.getByTestId('needs-locked')).toHaveCount(0);
      await answerCommute(page, 40, 3);
      await expect(page.getByTestId('needs-locked')).toBeVisible();
      await expect(rows(page).first().getByTestId('needs-locked-commute')).toBeVisible();

      // the open row points to the one gate in the card
      await rows(page).first().locator('button[aria-expanded]').click();
      await page.getByTestId('row-unlock').click();
      await expect(page.getByTestId('needs-gate')).toBeVisible();
      await expect(page.getByTestId('needs-unlock')).toHaveCount(0);
    });

    test('eingeloggt: Tagesstrecke ändert den Hinweis der ersten Zeile, kein Gate', async ({ page }) => {
      await login(page);
      await openRanking(page);
      await expect(page.getByTestId('needs-locked')).toHaveCount(0);
      const firstHint = rows(page).first().getByTestId('needs-hint');
      await answerCommute(page, 40, 3);
      await setSlider(page, 'longTrip', 400);
      // three answers: commute line and trip line
      await expect(firstHint).toContainText('Fürs Pendeln');
      await expect(firstHint).toContainText('400 km:');
      const hintBefore = await firstHint.innerText();

      await setSlider(page, 'commute', 120);
      await expect.poll(() => firstHint.innerText()).not.toBe(hintBefore);
    });

    test('Stopp-Chip: als Gast nennt er die Zahl und öffnet das Gate, eingeloggt filtert er', async ({ page }) => {
      await openRanking(page);
      await setSlider(page, 'longTrip', 250);
      await page.getByTestId('needs-stops-0').click();
      await expect(page.getByTestId('needs-gate')).toBeVisible();
      await expect(page.getByTestId('needs-gate')).toContainText(/Deine \d+ Modelle für 250 km sind bereit/);
      await expect(page.getByTestId('needs-stops-0')).toHaveAttribute('aria-pressed', 'false');

      await login(page);
      await openRanking(page);
      // 1.200 km schafft kein Modell ohne Stopp: die Kachel ist gesperrt, nichts filtert ins Leere
      await setSlider(page, 'longTrip', 1200);
      await expect(page.getByTestId('needs-stops-0')).toBeDisabled();

      await setSlider(page, 'longTrip', 250);
      const tile = page.getByTestId('needs-stops-0');
      await tile.click();
      await expect(tile).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId('needs-show-all')).toBeVisible();
      await tile.click();
      await expect(tile).toHaveAttribute('aria-pressed', 'false');
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

    test('Karte und Liste zeigen aufeinander: Kachel filtert, Ändern fokussiert die Eingabe', async ({ page }) => {
      await login(page);
      await openRanking(page);
      await answerCommute(page, 40, 3);
      await setSlider(page, 'longTrip', 250);
      await page.getByTestId('needs-stops-0').click();
      await expect(page.getByTestId('needs-stops-0')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.locator('section.mr-board')).toBeInViewport();

      // the basis line appears in the sticky header once the card has scrolled away
      await page.getByTestId('needs-change').waitFor({ state: 'hidden' });
      await rows(page).nth(5).scrollIntoViewIfNeeded();
      await page.mouse.wheel(0, 600);
      await expect(page.getByTestId('needs-change')).toBeVisible();
      await expect(page.locator('section.mr-board')).toContainText('Für Pendeln 40 km an 3 Tagen, 250 km längste Fahrt');
      await page.getByTestId('needs-change').click();
      // the first open slider: yearly km, the list never asked it
      await expect(page.getByTestId('slider-needs-annualKm')).toBeFocused();
    });
  });
}

import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

// Modellseite als Story "Prospekt gegen Realität": vier Kapitel, Spanne, Gate. ID.3 hat lokal Saison- und Streckendaten.
const URL = '/modelle/Volkswagen/ID.3';

async function openStory(page: Page) {
  await page.goto(URL);
  await expect(page.getByTestId('story-hero')).toBeVisible({ timeout: 15_000 });
}

/** Sets a range input like a release after dragging: input for the live label, change for the answer */
async function setSlider(page: Page, key: string, value: number) {
  await page.getByTestId(`slider-${key}`).evaluate((el, v) => {
    const input = el as HTMLInputElement;
    input.value = String(v);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }, value);
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

/** All five questions answered: two sliders, three chip questions */
async function answerAll(page: Page) {
  await setSlider(page, 'annualKm', 20000);
  await setSlider(page, 'highwayShare', 40);
  await setSlider(page, 'commute', 35);
  await page.getByTestId('chip-longTrip-500').click();
  await page.getByTestId('chip-place-true').click();
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
  test.describe(`Modellseite Story (${viewport.width} px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport);
    });

    test('H1, Story-Satz, vier Kapitel und JSON-LD stehen auf der Seite', async ({ page }) => {
      await openStory(page);
      await expect(page.locator('h1')).toContainText('Volkswagen ID.3');
      await expect(page.getByTestId('story-hero')).toContainText(/Fahrer sagen|verspricht/);
      await expect(page.locator('section[aria-labelledby^="story-"]')).toHaveCount(4);
      await expect(page.locator('script[type="application/ld+json"]').first()).toBeAttached();
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/modelle\/Volkswagen\/ID\.3$/);
    });

    test('ohne Login zeigt die Leiste keine Spanne und fragt den Server nicht, zählt aber die Antworten', async ({ page }) => {
      const previews: string[] = [];
      page.on('request', r => { if (r.url().includes('/api/public/personal-range/')) previews.push(r.url()); });
      await openStory(page);
      await expect(page.getByTestId('range-band-locked')).toContainText('Noch 5 Fragen');
      await expect(page.getByTestId('range-band')).toHaveCount(0);
      await setSlider(page, 'highwayShare', 60);
      await expect(page.getByTestId('slider-highwayShare-value')).toContainText('60 %');
      // 60 % of the 15,000 km start value
      await expect(page.getByTestId('slider-highwayShare-detail')).toContainText('9.000 km');
      await expect(page.getByTestId('range-band-bar')).toContainText('1 von 5');
      await expect(page.getByTestId('range-band-locked')).toContainText('Noch 4 Fragen');
      await expect(page.getByTestId('range-band')).toHaveCount(0);
      expect(previews).toEqual([]);
    });

    test('eingeloggt holt der Autobahn-Regler die Spanne für das Profil', async ({ page }) => {
      await login(page);
      await openStory(page);
      const preview = page.waitForResponse(r => r.url().includes('/api/public/personal-range/') && r.request().method() === 'POST');
      await setSlider(page, 'highwayShare', 60);
      const response = await preview;
      expect(response.request().postDataJSON()).toEqual(expect.objectContaining({ highwaySharePercent: 60 }));
      expect(response.status()).toBe(200);
      const body = await response.json();
      // only the two ends of the band leave the server before the exact result
      expect(Object.keys(body).sort()).toEqual(['dataLevel', 'maxKm', 'minKm']);
      await expect(page.getByTestId('range-band')).toContainText(String(body.maxKm));
    });

    test('beim Laden ist keine Antwort vorgewählt', async ({ page }) => {
      await openStory(page);
      await expect(page.locator('[data-testid^="chip-"][aria-pressed="true"]')).toHaveCount(0);
      await expect(page.getByTestId('slider-annualKm-value')).toHaveCount(0);
      await expect(page.getByTestId('slider-highwayShare-value')).toHaveCount(0);
    });

    test('die Pendelstrecke ist ein Regler und geht eingeloggt mit ihrem Wert an den Server', async ({ page }) => {
      await login(page);
      await openStory(page);
      const preview = page.waitForResponse(r => r.url().includes('/api/public/personal-range/') && r.request().method() === 'POST');
      await setSlider(page, 'commute', 35);
      await expect(page.getByTestId('slider-commute-value')).toContainText('35 km');
      expect((await preview).request().postDataJSON()).toEqual(expect.objectContaining({ commuteKmPerDay: 35 }));
    });

    test('die Antworten der Rangliste zählen auf der Modellseite als beantwortet', async ({ page }) => {
      await page.goto('/modelle');
      await page.evaluate(() => localStorage.setItem('ev-needs-check', JSON.stringify({ dailyKm: 35, longestTripKm: 800, homeCharging: false, maxStops: 0 })));
      await openStory(page);
      await expect(page.getByTestId('range-band-bar')).toContainText('3 von 5');
      await expect(page.getByTestId('range-band-locked')).toContainText('Noch 2 Fragen');
      await expect(page.getByTestId('chip-longTrip-800')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId('chip-place-false')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId('slider-commute-value')).toContainText('35 km');
    });

    test('der Jahresregler rechnet die Autobahn-Kilometer mit', async ({ page }) => {
      await openStory(page);
      await setSlider(page, 'highwayShare', 50);
      await setSlider(page, 'annualKm', 30000);
      await expect(page.getByTestId('slider-annualKm-value')).toContainText('30.000 km');
      await expect(page.getByTestId('slider-highwayShare-detail')).toContainText('15.000 km');
      await expect(page.getByTestId('range-band-bar')).toContainText('2 von 5');
    });

    test('"Meine Zahlen" mit offenen Fragen führt zur ersten offenen Frage statt zum Gate', async ({ page }) => {
      await openStory(page);
      await setSlider(page, 'annualKm', 20000);
      await page.getByTestId('range-band-cta').click();
      await expect(page.getByTestId('inline-gate')).toHaveCount(0);
      await expect(page.locator('#story-highwayShare')).toHaveClass(/ring-2/);
      await expect(page.getByTestId('slider-highwayShare')).toBeFocused();
      await expect(page.getByTestId('personal-range-open')).toContainText('Noch 4 Fragen offen');
    });

    test('"Meine Zahlen" öffnet nach allen Antworten das Gate, ohne Zahlen preiszugeben', async ({ page }) => {
      await openStory(page);
      await answerAll(page);
      const placeholder = page.getByTestId('personal-range-placeholder');
      await expect(placeholder).toBeAttached();
      expect(await placeholder.locator('dd').allInnerTexts()).toEqual(expect.arrayContaining([expect.not.stringMatching(/\d/)]));
      await page.getByTestId('range-band-cta').click();
      await expect(page.getByTestId('inline-gate')).toBeVisible();
      await expect(page.getByTestId('personal-range-result')).toHaveCount(0);
    });

    test('Hero-Sätze und Tacho-Legende bleiben je einzeilig', async ({ page }) => {
      await openStory(page);
      for (const line of await page.getByTestId('story-hero').locator('span').all()) {
        const box = await line.boundingBox();
        const lineHeight = await line.evaluate(el => parseFloat(getComputedStyle(el).lineHeight));
        expect(box!.height).toBeLessThan(lineHeight * 1.5);
      }
      const legend = page.locator('figcaption');
      const height = (await legend.boundingBox())!.height;
      expect(height).toBeLessThan(24);
    });

    test('kein horizontales Scrollen', async ({ page }) => {
      await openStory(page);
      const { scrollWidth, clientWidth } = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
    });
  });
}

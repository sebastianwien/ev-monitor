import { test, expect, type Page } from '@playwright/test';

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

    test('der Autobahn-Regler zählt als Antwort und holt die Spanne für das Profil ohne Login', async ({ page }) => {
      await openStory(page);
      await expect(page.getByTestId('range-band')).toBeVisible();
      const preview = page.waitForResponse(r => r.url().includes('/api/public/personal-range/') && r.request().method() === 'POST');
      await setSlider(page, 'highwayShare', 60);
      await expect(page.getByTestId('slider-highwayShare-value')).toContainText('60 %');
      // 60 % of the 15,000 km start value
      await expect(page.getByTestId('slider-highwayShare-detail')).toContainText('9.000 km');
      await expect(page.getByTestId('range-band-bar')).toContainText('1 von 5');
      const response = await preview;
      expect(response.request().postDataJSON()).toEqual(expect.objectContaining({ highwaySharePercent: 60, annualKm: 15000 }));
      expect(response.status()).toBe(200);
      const body = await response.json();
      // only the two ends of the band leave the server before the gate
      expect(Object.keys(body).sort()).toEqual(['dataLevel', 'maxKm', 'minKm']);
      await expect(page.getByTestId('range-band')).toContainText(String(body.maxKm));
    });

    test('beim Laden ist keine Antwort vorgewählt', async ({ page }) => {
      await openStory(page);
      await expect(page.locator('[data-testid^="chip-"][aria-pressed="true"]')).toHaveCount(0);
      await expect(page.getByTestId('slider-annualKm-value')).toHaveCount(0);
      await expect(page.getByTestId('slider-highwayShare-value')).toHaveCount(0);
    });

    test('die Pendelstrecke ist ein Regler und geht mit ihrem Wert an den Server', async ({ page }) => {
      await openStory(page);
      const preview = page.waitForResponse(r => r.url().includes('/api/public/personal-range/') && r.request().method() === 'POST');
      await setSlider(page, 'commute', 35);
      await expect(page.getByTestId('slider-commute-value')).toContainText('35 km');
      expect((await preview).request().postDataJSON()).toEqual(expect.objectContaining({ commuteKmPerDay: 35 }));
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

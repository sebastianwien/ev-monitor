import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

// Modellseite: Hero, Datenblatt, Fahrprofil mit Ergebnis-Box, Belege, Details. ID.3 hat lokal Saison- und Streckendaten.
const URL = '/modelle/Volkswagen/ID.3';

async function openStory(page: Page) {
  await page.goto(URL);
  await expect(page.getByTestId('story-hero')).toBeVisible({ timeout: 15_000 });
}

/** Signed in: the stored profile lands shortly after the page and moves the sliders; wait for it first */
async function openStoryWithProfile(page: Page) {
  const profile = page.waitForResponse(r => r.url().includes('/api/personal-range/profile'));
  await openStory(page);
  await profile;
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

/** All five sliders answered */
async function answerAll(page: Page) {
  await setSlider(page, 'annualKm', 20000);
  await setSlider(page, 'highwayShare', 40);
  await setSlider(page, 'commute', 35);
  await setSlider(page, 'commuteDays', 3);
  await setSlider(page, 'longTrip', 500);
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
  test.describe(`Modellseite (${viewport.width} px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport);
    });

    test('H1, Hero-Satz, Datenblatt, Belege und JSON-LD stehen auf der Seite', async ({ page }) => {
      await openStory(page);
      await expect(page.locator('h1')).toContainText('Volkswagen ID.3');
      await expect(page.getByTestId('story-hero')).toContainText(/Fahrer sagen|verspricht/);
      await expect(page.getByTestId('model-facts').locator('[data-testid^="fact-"]')).toHaveCount(4);
      await expect(page.getByTestId('model-evidence')).toContainText('Reichweite nach Jahreszeit');
      await expect(page.locator('script[type="application/ld+json"]').first()).toBeAttached();
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/modelle\/Volkswagen\/ID\.3$/);
    });

    test('ein Regler gilt erst bewegt als beantwortet, die Schloss-Box zählt mit', async ({ page }) => {
      await openStory(page);
      await expect(page.getByTestId('personal-range-open')).toContainText('Noch 5 Regler offen');
      await expect(page.getByTestId('slider-highwayShare-value')).toContainText('noch offen');
      await setSlider(page, 'highwayShare', 60);
      await expect(page.getByTestId('slider-highwayShare-value')).toContainText('60 %');
      // 60 % of the 15.000 km start value
      await expect(page.getByTestId('slider-highwayShare-detail')).toContainText('9.000 km');
      await expect(page.getByTestId('personal-range-open')).toContainText('Noch 4 Regler offen');
    });

    test('Freischalten mit offenen Reglern springt zum ersten offenen Regler statt zum Gate', async ({ page }) => {
      await openStory(page);
      await setSlider(page, 'annualKm', 20000);
      await page.getByTestId('personal-range-calculate').click();
      await expect(page.getByTestId('inline-gate')).toHaveCount(0);
      await expect(page.locator('#story-highwayShare')).toHaveClass(/ring-2/);
      await expect(page.getByTestId('slider-highwayShare')).toBeFocused();
    });

    test('Freischalten nach allen Antworten öffnet das Gate in der Box, ohne Zahlen preiszugeben', async ({ page }) => {
      await openStory(page);
      await answerAll(page);
      const placeholder = page.getByTestId('personal-range-placeholder');
      for (const text of await placeholder.locator('dd').allInnerTexts()) expect(text).not.toMatch(/\d/);
      await expect(page.getByTestId('personal-range-open')).toContainText('Deine 4 Antworten sind bereit');
      await page.getByTestId('personal-range-calculate').click();
      await expect(page.getByTestId('inline-gate')).toBeVisible();
      await expect(page.getByTestId('personal-range-result')).toHaveCount(0);
    });

    test('eingeloggt: vier Antworten, Wallbox-Frage rechnet neu und geht an die Rangliste', async ({ page }) => {
      await login(page);
      await openStoryWithProfile(page);
      const first = page.waitForResponse(r => /\/api\/personal-range\/Volkswagen\/ID\.3$/.test(r.url()) && r.request().method() === 'POST');
      await answerAll(page);
      const request = (await first).request().postDataJSON();
      expect(request).toEqual(expect.objectContaining({ commuteKmPerDay: 35, commuteDaysPerWeek: 3, longTripKm: 500 }));
      const result = page.getByTestId('personal-range-result');
      // the stored profile of an earlier run may hold "no wallbox": start from "yes" on purpose
      if (await page.getByTestId('wallbox-yes').getAttribute('aria-pressed') !== 'true') {
        const home = page.waitForResponse(r => /\/api\/personal-range\/Volkswagen\/ID\.3$/.test(r.url()) && r.request().postDataJSON()?.homeCharging === true);
        await page.getByTestId('wallbox-yes').click();
        await home;
      }
      await expect(result.getByTestId('answer-rhythm')).toContainText(/(Im Winter|Sommer wie Winter) (alle \d+ (Tage|Wochen)|täglich)/);
      await expect(result.getByTestId('answer-stops')).toContainText(/Urlaubsfahrt über 500\skm wirst du wahrscheinlich/);

      const again = page.waitForResponse(r => /\/api\/personal-range\/Volkswagen\/ID\.3$/.test(r.url()) && r.request().method() === 'POST' && r.request().postDataJSON().homeCharging === false);
      await page.getByTestId('wallbox-no').click();
      await again;
      await expect(page.getByTestId('wallbox-no')).toHaveAttribute('aria-pressed', 'true');
      await expect(result.getByTestId('answer-rhythm')).toContainText(/-mal pro Woche/);
      const needs = await page.evaluate(() => Object.values(localStorage).find(v => v.includes('homeCharging')) ?? '');
      expect(needs).toContain('"homeCharging":false');
    });

    test('Hero-Sätze und Tacho-Legende bleiben je einzeilig', async ({ page }) => {
      await openStory(page);
      for (const line of await page.getByTestId('story-hero').locator('span').all()) {
        const box = await line.boundingBox();
        const lineHeight = await line.evaluate(el => parseFloat(getComputedStyle(el).lineHeight));
        expect(box!.height).toBeLessThan(lineHeight * 1.5);
      }
      const legend = page.locator('header figcaption');
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

test('Handy: die Freischalten-Leiste kommt erst, wenn der Leser die Box kennt, und nur solange sie aus dem Bild ist', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openStory(page);
  // on arrival the bar would cover the data sheet
  await expect(page.getByTestId('unlock-bar')).toHaveCount(0);
  await page.getByTestId('personal-range').scrollIntoViewIfNeeded();
  await expect(page.getByTestId('personal-range')).toBeInViewport();
  await expect(page.getByTestId('unlock-bar')).toHaveCount(0);
  // the observer reports the box once more on the next frame; then back to the top
  await page.waitForTimeout(200);
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect(page.getByTestId('unlock-bar')).toBeVisible();
});

import { test, expect } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

async function dismissAnnouncements(page: any) {
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
  }, keys);
}

async function login(page: any) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

test.describe('Story-Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await dismissAnnouncements(page);
    await login(page);
  });

  test('Teaser führt zur Story-Seite, Rückweg schaltet aus', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    // Mobil liegt das Dashboard ohne Seitenrand: der Teaser darf nicht über den Viewport ragen.
    const box = await page.getByTestId('story-teaser').boundingBox();
    const viewport = page.viewportSize();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(viewport!.width);

    await page.getByTestId('story-teaser').getByRole('button', { name: /Ansehen|Show me/ }).click();
    await expect(page).toHaveURL(/\/story/);
    await expect(page.getByTestId('story-view')).toBeVisible();
    await page.waitForLoadState('networkidle');

    // Opt-in aktiv: /dashboard leitet auf /story um
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/story/);

    await page.getByTestId('story-back').click();
    await expect(page).toHaveURL(/\/dashboard/);
    // Das alte Dashboard muss danach wirklich rendern: die Routen-Transition (out-in) blieb
    // frueher an einem Fragment-Root der Story haengen und liess die router-view leer.
    await expect(page.getByTestId('story-teaser')).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('Kacheln öffnen Sheets, Monat wechselt ohne Fehler', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.addInitScript(() => localStorage.setItem('story_dashboard_opt_in', 'true'));
    await page.goto('/story');
    await page.waitForLoadState('networkidle');

    // Jede sichtbare Kachel muss ein Sheet oeffnen und wieder schliessen koennen.
    for (const id of ['cost', 'flow', 'range', 'wltp', 'peer', 'battery']) {
      const tile = page.getByTestId(`story-tile-${id}`);
      if (!(await tile.isVisible())) continue;
      await tile.click();
      await expect(page.getByTestId('story-sheet')).toBeVisible();
      await page.getByTestId('story-sheet').getByRole('button').last().click();
      await expect(page.getByTestId('story-sheet')).toBeHidden();
    }

    await page.getByTestId('story-month--1').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('story-view')).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('Hero mit Fixkosten faechert Kostenschichten auf', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('story_dashboard_opt_in', 'true'));
    await page.goto('/story');
    await page.waitForLoadState('networkidle');

    const hero = page.getByTestId('story-hero-value');
    if (!(await hero.isVisible())) test.skip(true, 'Monat ohne Kosten');
    // Nur wenn der Hero ein Button ist (Fixkosten vorhanden), gibt es Schichten.
    const isButton = await hero.evaluate(el => el.tagName === 'BUTTON');
    if (!isButton) test.skip(true, 'Test-User ohne Fixkosten');
    await hero.click();
    await expect(page.getByTestId('story-cost-layers')).toBeVisible();
    await expect(hero).toHaveAttribute('aria-expanded', 'true');
    await hero.click();
    await expect(page.getByTestId('story-cost-layers')).toBeHidden();
  });
});

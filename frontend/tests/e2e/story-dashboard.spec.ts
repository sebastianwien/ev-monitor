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
    for (const id of ['cost', 'wltp', 'trips', 'flow', 'range', 'peer', 'battery']) {
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

  test('Kopf und Chips bleiben beim Wechsel Übersicht <-> Logs stehen, nur der Inhalt tauscht', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.addInitScript(() => localStorage.setItem('story_dashboard_opt_in', 'true'));
    await page.goto('/story');
    await expect(page.getByTestId('story-view')).toBeVisible();

    // Dieselben DOM-Elemente vorher und nachher: kein Neu-Rendern des Gerüsts (sonst flackert es)
    const head = await page.getByTestId('public-nav-settings').elementHandle();
    const chips = page.getByRole('navigation', { name: /Bereiche|Sections|Områder|Områden/ });
    const chipsHandle = await chips.elementHandle();
    await chips.getByRole('link', { name: /Ladevorgänge/ }).click();
    await expect(page).toHaveURL(/\/logs/);
    await expect(chips.getByRole('link', { name: /Ladevorgänge/ })).toHaveAttribute('aria-current', 'page');
    expect(await head!.evaluate(el => el.isConnected)).toBe(true);
    expect(await chipsHandle!.evaluate(el => el.isConnected)).toBe(true);

    await chips.getByRole('link', { name: /Dashboard/ }).click();
    await expect(page).toHaveURL(/\/story/);
    expect(await head!.evaluate(el => el.isConnected)).toBe(true);
  });

  test('Hero mit Fixkosten faechert Kostenschichten auf', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('story_dashboard_opt_in', 'true'));
    await page.goto('/story');
    await page.waitForLoadState('networkidle');

    const toggle = page.getByTestId('story-hero-layers');
    // Den Knopf gibt es nur in Monaten mit Fixkosten oder Einnahmen.
    if (!(await toggle.isVisible())) test.skip(true, 'Test-User ohne Fixkosten');
    await toggle.click();
    await expect(page.getByTestId('story-cost-layers')).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await toggle.click();
    await expect(page.getByTestId('story-cost-layers')).toBeHidden();
  });
});

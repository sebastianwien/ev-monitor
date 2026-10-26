import { test, expect, request as playwrightRequest, type Locator, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Der Tab ist der einzige Weg zu /charging-providers - bricht er, ist die
 * Ladekarten-Verwaltung unerreichbar.
 *
 * Welcher Tab das ist, haengt von der Breite ab: Desktop zeigt die Workspace-Leiste mit
 * allen vier Zielen (Chip-Links, der aktive mit aria-current), Mobile den Zweier-Switch
 * Fahrzeuge <-> Ladekarten (role=tab mit aria-selected).
 */
function areaTab(page: Page, isMobile: boolean, name: string): Locator {
  return isMobile
    ? page.locator('[role="tab"]:visible', { hasText: name })
    : page.getByRole('navigation', { name: 'Bereiche' }).getByRole('link', { name, exact: true })
}

async function expectSelected(tab: Locator, isMobile: boolean, selected: boolean) {
  if (isMobile) await expect(tab).toHaveAttribute('aria-selected', String(selected))
  else if (selected) await expect(tab).toHaveAttribute('aria-current', 'page')
  else await expect(tab).not.toHaveAttribute('aria-current', 'page')
}

test.describe('Fuhrpark-Tabs: Fahrzeuge <-> Ladekarten', () => {
  test.beforeEach(async ({ page }) => {
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);

    await page.goto('/login');
    await page.locator('input[type="text"]').fill(TEST_USER.email);
    await page.locator('input[type="password"]').fill(TEST_USER.password);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
  });

  test('Tab wechselt zwischen Fahrzeugen und Ladekarten', async ({ page, isMobile }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/cars');
    await page.waitForLoadState('networkidle');

    const cardsTab = areaTab(page, isMobile, 'Meine Ladekarten');
    await expect(cardsTab).toBeVisible();
    await expectSelected(cardsTab, isMobile, false);

    await cardsTab.click();
    await expect(page).toHaveURL(/\/charging-providers/);
    await expect(page.locator('h1:has-text("Meine Ladekarten")')).toBeVisible();
    await expectSelected(cardsTab, isMobile, true);

    // Zurueck: die Ladekarten-Verwaltung darf keine Sackgasse sein
    await areaTab(page, isMobile, 'Meine Fahrzeuge').click();
    await expect(page).toHaveURL(/\/cars/);
    await expect(page.locator('h1:has-text("Meine Fahrzeuge")')).toBeVisible();

    expect(errors).toEqual([]);
  });

  test('Desktop: Ladekarten und zurueck zum Dashboard in je einem Klick', async ({ page, isMobile }) => {
    test.skip(!!isMobile, 'Die Workspace-Leiste gibt es nur ab 768px - Mobile wischt stattdessen.');

    await page.waitForLoadState('networkidle');

    await areaTab(page, false, 'Meine Ladekarten').click();
    await expect(page).toHaveURL(/\/charging-providers/);
    await expect(page.locator('h1:has-text("Meine Ladekarten")')).toBeVisible();

    await areaTab(page, false, 'Dashboard').click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('Ladekarte im Tab anlegen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/charging-providers');
    await page.waitForLoadState('networkidle');

    await page.locator('button:has-text("Neue Ladekarte")').click();
    await page.locator('select').first().selectOption('Maingau Energie');
    await page.locator('button:has-text("Speichern")').click();

    await expect(page.locator('p:has-text("Maingau Energie")')).toBeVisible({ timeout: 5_000 });
    expect(errors).toEqual([]);
  });

  test.afterAll(async () => {
    // Portfolio des Testusers wieder leeren - der Ladekarten-Prompt im Log-Formular
    // zeigt sich nur ohne Karte, andere Specs verlassen sich darauf.
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    const authResp = await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    });
    const { token } = await authResp.json();
    const headers = { Authorization: `Bearer ${token}` };

    const cards = await (await api.get('/api/users/me/charging-providers', { headers })).json();
    for (const card of cards) {
      await api.delete(`/api/users/me/charging-providers/${card.id}`, { headers });
    }
  });
});

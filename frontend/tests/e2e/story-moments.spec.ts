import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

/**
 * Moment-Hero auf /story: die letzten Ladungen kommen gemockt, alles andere vom echten Backend.
 * So laesst sich jeder Zustand ohne Testdaten in der DB ausloesen.
 */
const log = (over: Record<string, unknown>) => ({
  id: '00000000-0000-0000-0000-00000000e2e1', carId: null, kwhCharged: 21, kwhAtVehicle: 20, costEur: null,
  socBeforeChargePercent: 30, socAfterChargePercent: 80, loggedAt: new Date().toISOString(),
  createdAt: new Date().toISOString(), dataSource: 'USER_LOGGED', includeInStatistics: true, ...over,
});

async function setup(page: Page, opts: { logs: unknown[]; alwaysMonth?: boolean }) {
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript(([seenKeys, alwaysMonth]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
    localStorage.setItem('story_dashboard_opt_in', 'true');
    if (alwaysMonth) localStorage.setItem('story_always_month', 'true');
  }, [keys, !!opts.alwaysMonth] as const);
  await page.route(/\/api\/logs\?carId=[^&]+&limit=10$/, route => route.fulfill({ json: opts.logs }));
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/(dashboard|story)/, { timeout: 10_000 });
  await page.goto('/story');
  await page.waitForLoadState('networkidle');
}

test.describe('Story Moment-Hero', () => {
  test('frische Ladung ohne Preis steht oben und führt zum Nachtragen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await setup(page, { logs: [log({ loggedAt: new Date(Date.now() - 10 * 60_000).toISOString() })] });

    const hero = page.getByTestId('story-moment-just_charged');
    await expect(hero).toBeVisible();
    await expect(hero.getByTestId('story-moment-sentence')).toContainText(/20,0\skWh, 30 → 80\s%/);
    await expect(hero.getByRole('link', { name: 'Preis nachtragen' })).toHaveAttribute('href', /nachtragen=preis/);
    // Mobil darf der Hero nicht über den Rand ragen.
    const box = await hero.boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    expect(errors).toEqual([]);
  });

  test('Stille mit AutoSync-Satz lässt sich wegklicken und bleibt weg', async ({ page }) => {
    const old = new Date(Date.now() - 20 * 86_400_000).toISOString();
    await setup(page, { logs: [log({ loggedAt: old, createdAt: old })] });

    const hero = page.getByTestId('story-moment-silence');
    await expect(hero).toContainText(/Letzte Ladung vor 20\sTagen/);
    await expect(hero).toContainText('kann Ladungen und Fahrten selbst eintragen');
    await hero.getByRole('button', { name: 'Alles erfasst' }).click();
    await expect(page.getByTestId('story-moment-silence')).toBeHidden();

    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('story-view')).toBeVisible();
    await expect(page.getByTestId('story-moment-silence')).toBeHidden();
  });

  test('Lücke fragt nach dem Preis, "Weiß ich nicht mehr" lässt sie verschwinden', async ({ page }) => {
    const twoDays = new Date(Date.now() - 2 * 86_400_000).toISOString();
    const gap = log({ id: '00000000-0000-0000-0000-00000000e2e2', loggedAt: twoDays, createdAt: twoDays, kwhAtVehicle: 28.5, priceUnknown: false });
    await page.route(/\/api\/logs\/priceless\?carId=[^&]+&includeUnknown=true$/, route => route.fulfill({ json: [gap] }));
    const marked: unknown[] = [];
    await page.route(/\/api\/logs\/[^/]+\/gap$/, route => { marked.push(route.request().postDataJSON()); return route.fulfill({ json: {} }); });
    await setup(page, { logs: [log({ loggedAt: twoDays, createdAt: twoDays, costEur: 9 })] });

    const hero = page.getByTestId('story-moment-gap');
    await expect(hero.getByTestId('story-moment-sentence')).toContainText(/Was haben die 28,5\skWh vom/);
    await hero.getByRole('button', { name: 'Weiß ich nicht mehr' }).click();
    await expect(page.getByTestId('story-moment-gap')).toBeHidden();
    expect(marked).toEqual([{ field: 'PRICE' }]);
  });

  test('"Immer den Monat zeigen" lässt den Moment weg', async ({ page }) => {
    await setup(page, { logs: [log({})], alwaysMonth: true });
    await expect(page.getByTestId('story-view')).toBeVisible();
    await expect(page.locator('[data-testid^="story-moment-"]')).toHaveCount(0);
  });
});

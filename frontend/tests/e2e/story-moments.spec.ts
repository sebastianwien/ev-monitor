import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';
import { waitForStory } from './fixtures/story';

/**
 * Karten von unten auf /story: die letzten Ladungen kommen gemockt, alles andere vom echten
 * Backend. So laesst sich jeder Zustand ohne Testdaten in der DB ausloesen.
 */
const log = (over: Record<string, unknown>) => ({
  id: '00000000-0000-0000-0000-00000000e2e1', carId: null, kwhCharged: 21, kwhAtVehicle: 20, costEur: null,
  socBeforeChargePercent: 30, socAfterChargePercent: 80, loggedAt: new Date().toISOString(),
  createdAt: new Date().toISOString(), dataSource: 'USER_LOGGED', includeInStatistics: true, ...over,
});

async function setup(page: Page, opts: { logs: unknown[] }) {
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript((seenKeys) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
    localStorage.setItem('story_note_seen', 'true');
    localStorage.setItem('story_dashboard_opt_in', 'true');
  }, keys);
  await page.route(/\/api\/logs\?carId=[^&]+&limit=10$/, route => route.fulfill({ json: opts.logs }));
  // Kein Rückblick und kein Ranglisten-Abschluss aus echten Daten: die Karten hier sollen allein stehen.
  await page.route(/\/api\/public\/leaderboard\/standings\/me/, route => route.fulfill({ json: [] }));
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/(dashboard|story)/, { timeout: 10_000 });
  await page.goto('/story');
  await waitForStory(page);
}

test.describe('Story: Karten von unten', () => {
  test('frische Ladung ohne Preis ist keine Karte: "Heute geladen" mit Knopf "Preis eintragen"', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await setup(page, { logs: [log({ loggedAt: new Date(Date.now() - 10 * 60_000).toISOString() })] });

    const today = page.getByTestId('story-latest-today');
    await expect(today).toContainText(/Heute geladen/);
    await expect(today).toContainText(/20,0\skWh ab 30\s%/);
    await expect(today.getByRole('button', { name: 'Preis eintragen' })).toBeVisible();
    await expect(page.getByTestId('story-news-gap')).toHaveCount(0);
    const box = await today.boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    expect(errors).toEqual([]);
  });

  test('Stille mit AutoSync-Satz: Karte von unten, "Alles erfasst" heißt erledigt und bleibt weg', async ({ page }) => {
    const old = new Date(Date.now() - 20 * 86_400_000).toISOString();
    await setup(page, { logs: [log({ loggedAt: old, createdAt: old })] });

    const card = page.getByTestId('story-news-silence');
    await expect(card).toContainText(/Letzte Ladung vor 20\sTagen/);
    await expect(card).toContainText('kann Ladungen und Fahrten selbst eintragen');
    // Tun-Knopf in ganzer Breite, Karte über der Navigation, nicht über den Rand
    const button = card.getByRole('link', { name: 'Ladung erfassen' });
    await expect(button).toBeVisible();
    const box = await page.getByTestId('story-news').boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    expect(box!.y + box!.height).toBeGreaterThanOrEqual(page.viewportSize()!.height - 1);
    await card.getByRole('button', { name: 'Alles erfasst' }).click();
    await expect(page.getByTestId('story-news-silence')).toBeHidden();

    await page.reload();
    await waitForStory(page);
    await expect(page.getByTestId('story-news-silence')).toBeHidden();
    await expect(page.getByTestId('story-news-tab')).toHaveCount(0);
  });

  test('Tipp neben die Karte klappt sie zum Reiter ein, der Reiter klappt sie wieder auf', async ({ page }) => {
    const old = new Date(Date.now() - 20 * 86_400_000).toISOString();
    await setup(page, { logs: [log({ loggedAt: old, createdAt: old })] });
    await expect(page.getByTestId('story-news-silence')).toBeVisible();
    await page.getByTestId('story-news-backdrop').click({ position: { x: 20, y: 20 } });
    await expect(page.getByTestId('story-news')).toHaveCount(0);
    const tab = page.getByTestId('story-news-tab');
    await expect(tab).toHaveText(/1 Neuigkeit/);
    // Der Reiter steht über der Navigation (3,5 rem) und innerhalb des Bildschirms
    const tabBox = await tab.boundingBox();
    const h = page.viewportSize()!.height;
    expect(tabBox!.y + tabBox!.height).toBeLessThanOrEqual(h - 56);
    expect(tabBox!.y + tabBox!.height).toBeGreaterThan(h - 140);
    await tab.click();
    await expect(page.getByTestId('story-news-silence')).toBeVisible();
  });

  test('Lücke fragt nach dem Preis, "Weiß ich nicht mehr" lässt die Karte verschwinden', async ({ page }) => {
    const twoDays = new Date(Date.now() - 2 * 86_400_000).toISOString();
    const gap = log({ id: '00000000-0000-0000-0000-00000000e2e2', loggedAt: twoDays, createdAt: twoDays, kwhAtVehicle: 28.5, priceUnknown: false });
    await page.route(/\/api\/logs\/priceless\?carId=[^&]+&includeUnknown=true$/, route => route.fulfill({ json: [gap] }));
    const marked: unknown[] = [];
    await page.route(/\/api\/logs\/[^/]+\/gap$/, route => { marked.push(route.request().postDataJSON()); return route.fulfill({ json: {} }); });
    await setup(page, { logs: [log({ loggedAt: twoDays, createdAt: twoDays, costEur: 9 })] });

    const card = page.getByTestId('story-news-gap');
    await expect(card.getByTestId('story-news-sentence')).toContainText(/Was haben die 28,5\skWh vom/);
    await expect(card.getByRole('link', { name: 'Preis nachtragen' })).toHaveAttribute('href', /nachtragen=preis/);
    await card.getByRole('button', { name: 'Weiß ich nicht mehr' }).click();
    await expect(page.getByTestId('story-news-gap')).toBeHidden();
    expect(marked).toEqual([{ field: 'PRICE' }]);
  });

  test('zu teuer geladen nennt, was andere Fahrer zahlen, die Karten stehen im Sheet', async ({ page }) => {
    const threeHours = new Date(Date.now() - 3 * 3_600_000).toISOString();
    const expensive = log({ loggedAt: threeHours, createdAt: threeHours, kwhCharged: 40, costEur: 31.6, isPublicCharging: true, cpoName: 'IONITY', chargingType: 'DC' });
    const asked: string[] = [];
    await page.route(/\/api\/charging-benchmarks\?/, route => {
      asked.push(new URL(route.request().url()).search);
      return route.fulfill({ json: {
        cpoName: 'IONITY', chargingType: 'DC', drivers: 18, medianPricePerKwh: 0.52, lowerQuartilePricePerKwh: 0.39, upperQuartilePricePerKwh: 0.6,
        cards: [{ name: 'IONITY', variant: 'Motion', pricePerKwh: 0.49, monthlyFeeEur: 5.99, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' }],
      } });
    });
    await page.route(/\/api\/logs\/priceless\?carId=[^&]+&includeUnknown=true$/, route => route.fulfill({ json: [] }));
    await setup(page, { logs: [expensive] });

    // Login landet schon auf /story, setup laedt die Seite danach noch einmal: je Seitenaufruf eine Abfrage.
    expect(new Set(asked)).toEqual(new Set(['?cpo=IONITY&type=DC']));
    const card = page.getByTestId('story-news-expensive');
    await expect(card.getByTestId('story-news-sentence')).toContainText(/79\sct\/kWh bei IONITY\. Andere Fahrer zahlen dort im Schnitt 52\sct\/kWh, mit einer Ladekarte ab 49\sct\/kWh\./);
    await expect(card).not.toContainText('Motion');
    await card.getByRole('button', { name: 'Preise anderer Fahrer ansehen' }).click();
    const sheet = page.getByTestId('story-price-sheet');
    await expect(sheet).toContainText('Du hast 79,0');
    await expect(sheet.getByTestId('price-card')).toContainText('IONITY Motion');
    const box = await sheet.boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  });
});

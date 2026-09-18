import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

/**
 * VW EU-Data-Act-AutoSync-Karte auf der Import-Seite. Der Connectors-Service laeuft im E2E
 * nicht - seine Endpunkte werden gemockt. Geprueft wird der Nutzer-Workflow: Formular mit
 * Transparenz-Hinweisen, Verbinden, Status-Karte, sprechende Fehler, Free-Teaser.
 */

const statusActive = (carId: string) => ({
  carId, brand: 'skoda', email: 'max@example.com', vin: 'TMBTESTVIN0000001',
  status: 'ACTIVE', lastSuccessAt: null, historyImportedAt: null, lastError: null,
});

/**
 * Macht die Fahrzeuge des Testnutzers zu Skodas (die Karte gibt es nur fuer VW-Group-Marken)
 * und liest die echte Fahrzeug-ID mit.
 */
async function useSkodaCars(page: Page): Promise<{ value: string }> {
  const captured = { value: '' };
  await page.route('**/api/cars', async route => {
    const response = await route.fetch();
    const cars = (await response.json() as { id: string; brand: string }[]).map(c => ({ ...c, brand: 'SKODA' }));
    if (cars.length > 0) captured.value = cars[0].id;
    await route.fulfill({ response, body: JSON.stringify(cars) });
  });
  return captured;
}

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

async function mockPremium(page: Page, isPremium: boolean) {
  await page.route('**/api/subscription/status', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ isPremium, premiumEnabled: true, tier: isPremium ? 'AUTOSYNC' : 'NONE' }),
  }));
}

async function openEudaTab(page: Page) {
  await page.goto('/imports');
  await page.getByRole('button', { name: /VW Gruppe \(EU Data Act\)/ }).click();
}

test.describe('EU Data Act AutoSync', () => {
  test.beforeEach(async ({ page }) => {
    // Announcement-Modals wuerden den Klick auf die Import-Sektion abfangen
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);
    await login(page);
  });

  test('Beta: Free-Nutzer sehen die AutoSync-Karte nicht, der Upload bleibt', async ({ page }) => {
    await mockPremium(page, false);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await openEudaTab(page);

    await expect(page.getByText('Automatisch synchronisieren (VW EU Data Act)')).toHaveCount(0);
    await expect(page.locator('#euda-password')).toHaveCount(0);
    await expect(page.getByText('Export-Datei (.json oder .zip)')).toBeVisible();
  });

  test('Ohne VW-Group-Fahrzeug gibt es keine AutoSync-Karte, auch fuer Premium', async ({ page }) => {
    await mockPremium(page, true);
    await page.route('**/api/cars', async route => {
      const response = await route.fetch();
      const cars = (await response.json() as { brand: string }[]).map(c => ({ ...c, brand: 'TESLA' }));
      await route.fulfill({ response, body: JSON.stringify(cars) });
    });
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await openEudaTab(page);

    await expect(page.getByText('Export-Datei (.json oder .zip)')).toBeVisible();
    await expect(page.getByText('Automatisch synchronisieren (VW EU Data Act)')).toHaveCount(0);
  });

  test('Verbinden: Passwort geht genau einmal raus, danach Status-Karte', async ({ page }) => {
    await mockPremium(page, true);
    await useSkodaCars(page);
    let statusCalls = 0;
    await page.route('**/api/eu-data-act/status', route => {
      statusCalls++;
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    let connectBody: Record<string, string> | null = null;
    await page.route('**/api/eu-data-act/cars/*/connect', route => {
      connectBody = route.request().postDataJSON() as Record<string, string>;
      const carId = route.request().url().match(/cars\/([^/]+)\/connect/)![1];
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(statusActive(carId)) });
    });
    await openEudaTab(page);

    // Transparenz-Hinweise stehen im Formular, bevor der Nutzer etwas eingibt
    await expect(page.getByText(/nicht gespeichert/)).toBeVisible();
    await expect(page.getByText(/Datenanfrage an/)).toBeVisible();
    await expect(page.getByText(/Smartcar/).first()).toBeVisible();

    await page.getByRole('radio', { name: 'Škoda' }).click();
    await page.locator('#euda-email').fill('Max@Example.com');
    await page.locator('#euda-password').fill('geheim-123');
    await page.getByTestId('euda-connect').click();

    await expect(page.getByText('Verbunden - Ladevorgänge kommen automatisch')).toBeVisible();
    await expect(page.getByText('max@example.com')).toBeVisible();
    expect(connectBody).toEqual({ brand: 'skoda', email: 'Max@Example.com', password: 'geheim-123' });
    expect(statusCalls).toBeGreaterThan(0);
    await expect(page.locator('#euda-password')).toHaveCount(0);
    await expect(page.getByTestId('euda-history')).toBeVisible();
    await expect(page.getByTestId('euda-disconnect')).toBeVisible();
  });

  test('Falsches Passwort zeigt den passenden Fehler, Formular bleibt', async ({ page }) => {
    await mockPremium(page, true);
    await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    // 422, nicht 401 - ein 401 wuerde der Axios-Interceptor als abgelaufene Sitzung deuten
    await page.route('**/api/eu-data-act/cars/*/connect', route => route.fulfill({
      status: 422, contentType: 'application/json',
      body: JSON.stringify({ code: 'INVALID_CREDENTIALS', message: 'E-Mail oder Passwort falsch' }),
    }));
    await openEudaTab(page);

    await page.locator('#euda-email').fill('max@example.com');
    await page.locator('#euda-password').fill('falsch');
    await page.getByTestId('euda-connect').click();

    await expect(page.getByRole('alert')).toContainText('E-Mail oder Passwort falsch');
    await expect(page.locator('#euda-password')).toHaveValue('');
  });

  test('Bestehende Verbindung: Status, Historie anfordern, Trennen', async ({ page }) => {
    await mockPremium(page, true);
    const carId = await useSkodaCars(page);
    let connected = true;
    await page.route('**/api/eu-data-act/status', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(connected ? [statusActive(carId.value)] : []),
    }));
    let historyRequested = false;
    await page.route('**/api/eu-data-act/cars/*/history', route => { historyRequested = true; return route.fulfill({ status: 202 }); });
    await page.route('**/api/eu-data-act/cars/*', route => {
      if (route.request().method() === 'DELETE') { connected = false; return route.fulfill({ status: 204 }); }
      return route.fallback();
    });
    await openEudaTab(page);

    await expect(page.getByText('Verbunden - Ladevorgänge kommen automatisch')).toBeVisible();
    await page.getByTestId('euda-history').click();
    await expect(page.getByText(/Ladehistorie angefordert/)).toBeVisible();
    expect(historyRequested).toBe(true);

    await page.getByTestId('euda-disconnect').click();
    await expect(page.locator('#euda-password')).toBeVisible();
  });
});

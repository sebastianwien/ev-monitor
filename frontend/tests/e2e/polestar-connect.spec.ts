import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

/**
 * Polestar Data Portal Testmodus auf der Import-Seite. Der Connectors-Service wird gemockt.
 * Geprueft wird der Nutzer-Workflow mobil: Anleitung, VIN-Feld, Erfolg, "noch nicht freigegeben", Warteliste.
 */

const VIN = 'LPSED3KAXNL123456';

/** Macht die Fahrzeuge des Testnutzers zu Polestar (die Sektion gibt es nur dafuer). */
async function usePolestarCars(page: Page) {
  await page.route('**/api/cars', async route => {
    const response = await route.fetch();
    const cars = (await response.json() as { brand: string }[]).map(c => ({ ...c, brand: 'POLESTAR' }));
    await route.fulfill({ response, body: JSON.stringify(cars) });
  });
}

async function mockCapacity(page: Page, slotsFree: number) {
  await page.route('**/api/polestar/capacity', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ maxVehicles: 5, active: 5 - slotsFree, slotsFree }),
  }));
}

async function mockConnections(page: Page, body: unknown[]) {
  await page.route('**/api/polestar/connections', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(body),
  }));
}

async function mockConnect(page: Page, result: 'ACTIVE' | 'NOT_SHARED' | 'WAITLIST') {
  await page.route('**/api/polestar/connect', route => {
    const carId = (route.request().postDataJSON() as { carId: string }).carId;
    const body = result === 'ACTIVE'
      ? { id: 'conn-1', carId, vin: VIN, status: 'ACTIVE', connectedAt: new Date().toISOString(), lastEventAt: null, snapshotCount: 0, result }
      : { id: null, carId, vin: null, status: null, connectedAt: null, lastEventAt: null, snapshotCount: 0, result };
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
}

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

async function openPolestarSection(page: Page) {
  await page.goto('/imports');
  const tab = page.getByRole('button', { name: /Polestar Data Portal/ });
  await tab.waitFor();
  await page.waitForLoadState('networkidle');
  if (!(await page.getByTestId('polestar-integration').isVisible().catch(() => false))) await tab.click();
  await expect(page.getByTestId('polestar-integration')).toBeVisible();
}

test.describe('Polestar Data Portal Testmodus', () => {
  test.beforeEach(async ({ page }) => {
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);
    await login(page);
    await usePolestarCars(page);
    await mockCapacity(page, 4);
  });

  test('Alle Plaetze belegt: Einleitung und Warteliste, keine Anleitung', async ({ page }) => {
    await page.unroute('**/api/polestar/capacity');
    await mockCapacity(page, 0);
    await mockConnections(page, []);
    await page.goto('/imports');
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('polestar-intro')).toBeVisible();
    await expect(page.getByTestId('polestar-capped')).toContainText('Testplätze');
    await expect(page.getByTestId('polestar-vin')).toHaveCount(0);
  });

  test('Polestar aktiv: Sektion startet aufgeklappt mit Anleitung, Account-ID und Testmodus-Satz', async ({ page }) => {
    await mockConnections(page, []);
    await page.goto('/imports');
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('polestar-integration')).toBeVisible();
    await expect(page.getByTestId('polestar-step1')).toContainText('polestar@ev-monitor.net');
    await expect(page.getByRole('link', { name: /data-portal.polestar.com/ })).toHaveAttribute('href', 'https://data-portal.polestar.com');
    await expect(page.getByTestId('polestar-testmode')).toContainText('keine Position');
    await expect(page.getByTestId('polestar-vin')).toBeVisible();
  });

  test('Erfolg: VIN eintragen, pruefen, Status-Karte mit Trennen', async ({ page }) => {
    // Nach dem Verbinden laedt die Karte die Liste neu: dann liegt die Verbindung mit der echten carId vor.
    let connectedCarId: string | null = null;
    const row = (carId: string) => ({ id: 'conn-1', carId, vin: VIN, status: 'ACTIVE', connectedAt: new Date().toISOString(), lastEventAt: null, snapshotCount: 0, result: 'ACTIVE' });
    await page.route('**/api/polestar/connections', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(connectedCarId ? [row(connectedCarId)] : []),
    }));
    await page.route('**/api/polestar/connect', route => {
      const req = route.request().postDataJSON() as { carId: string; vin: string };
      expect(req.vin).toBe(VIN);
      connectedCarId = req.carId;
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(row(req.carId)) });
    });
    await openPolestarSection(page);
    await page.getByTestId('polestar-vin').fill(VIN.toLowerCase());
    await page.getByTestId('polestar-connect').click();
    await expect(page.getByTestId('polestar-status')).toContainText('Verbunden, Testmodus');
    await expect(page.getByTestId('polestar-last-data')).toContainText('noch keine Daten');
    await expect(page.getByTestId('polestar-disconnect')).toBeVisible();
    await expect(page.getByTestId('polestar-vin')).toHaveCount(0);
  });

  test('Noch nicht freigegeben: Hinweis bleibt mit Formular, erneut pruefen moeglich', async ({ page }) => {
    await mockConnections(page, []);
    await mockConnect(page, 'NOT_SHARED');
    await openPolestarSection(page);
    await page.getByTestId('polestar-vin').fill(VIN);
    await page.getByTestId('polestar-connect').click();
    await expect(page.getByTestId('polestar-not-shared')).toContainText('noch nicht');
    await expect(page.getByTestId('polestar-connect')).toBeEnabled();
  });

  test('Warteliste: Testplaetze belegt', async ({ page }) => {
    await mockConnections(page, []);
    await mockConnect(page, 'WAITLIST');
    await openPolestarSection(page);
    await page.getByTestId('polestar-vin').fill(VIN);
    await page.getByTestId('polestar-connect').click();
    await expect(page.getByTestId('polestar-waitlist')).toContainText('Testplätze');
  });

  test('Ungueltige VIN wird clientseitig abgefangen', async ({ page }) => {
    await mockConnections(page, []);
    let posted = false;
    await page.route('**/api/polestar/connect', route => { posted = true; return route.fulfill({ status: 400 }); });
    await openPolestarSection(page);
    await page.getByTestId('polestar-vin').fill('LPSED3KAINL12345');
    await page.getByTestId('polestar-connect').click();
    await expect(page.getByTestId('polestar-vin-error')).toBeVisible();
    expect(posted).toBe(false);
  });

  test('Ohne Polestar in der Garage gibt es keine Sektion', async ({ page }) => {
    await page.unroute('**/api/cars');
    await page.route('**/api/cars', async route => {
      const response = await route.fetch();
      const cars = (await response.json() as { brand: string }[]).map(c => ({ ...c, brand: 'SKODA' }));
      await route.fulfill({ response, body: JSON.stringify(cars) });
    });
    await page.goto('/imports');
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('polestar-section')).toHaveCount(0);
  });
});

import { test, expect, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

/**
 * VW EU-Data-Act-AutoSync-Karte auf der Import-Seite. Der Connectors-Service laeuft im E2E
 * nicht - seine Endpunkte werden gemockt. Geprueft wird der Nutzer-Workflow: Formular mit
 * Transparenz-Hinweisen, Verbinden, Status-Karte, sprechende Fehler, Trial-Hinweis, Teaser, Pause.
 */

const statusActive = (carId: string) => ({
  carId, brand: 'skoda', email: 'max@example.com', vin: 'TMBTESTVIN0000001',
  status: 'ACTIVE', lastSuccessAt: null, historyImportedAt: null, lastError: null,
});

/** Sync-Protokoll einer frisch verbundenen Verbindung: Datenanfrage laeuft, noch keine Lieferung. */
async function mockFreshActivity(page: Page) {
  await page.route('**/api/eu-data-act/cars/*/activity', route => {
    const carId = route.request().url().match(/cars\/([^/]+)\/activity/)![1];
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      provider: 'VW_GROUP', manufacturerContact: 'euda-support@cariad.technology',
      connection: {
        carId, brand: 'skoda', status: 'ACTIVE', connectedAt: new Date().toISOString(),
        lastPolledAt: null, lastSuccessAt: null, consecutiveFailures: 0, lastError: null,
        dataRequestActive: true, lastDeliveryAt: null, lastDataAt: null, history: null,
      },
      identifiers: [],
      summary: { deliveriesSeen: 0, deliveriesWithContent: 0, sessionsImported: 0, lastContentAt: null },
      polls: [], deliveries: [],
    }) });
  });
}
const WAITING_FIRST = 'Verbunden, warte auf die erste Lieferung mit Inhalt (bis zu 24 Stunden).';

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

/** Berechtigung kommt vom Core: Abo, Rolle oder launch-verankertes Trial. */
async function mockEntitlement(page: Page, e: { entitled: boolean; viaTrial: boolean; trialEndsAt: string | null }) {
  await page.route('**/api/subscription/eu-data-act-autosync', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(e),
  }));
}
const PAID = { entitled: true, viaTrial: false, trialEndsAt: null };
const TRIAL = { entitled: true, viaTrial: true, trialEndsAt: '2026-10-21' };
const TRIAL_OVER = { entitled: false, viaTrial: false, trialEndsAt: '2026-10-21' };

const UPLOAD_LABEL = 'Export-Datei (.json oder .zip)';

/** Oeffnet den Tab - oder laesst ihn offen, wenn er (VW-Group-Auto aktiv) schon aufgeklappt startet. */
async function openEudaTab(page: Page) {
  await page.goto('/imports');
  const tab = page.getByRole('button', { name: /VW Gruppe \(EU Data Act\)/ });
  await tab.waitFor();
  // Der Default-Tab wird erst nach dem Laden der Autos gesetzt - vorher zu klicken wuerde ihn
  // gleich wieder zuklappen.
  await page.waitForLoadState('networkidle');
  if (!(await page.getByText(UPLOAD_LABEL).isVisible().catch(() => false))) await tab.click();
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

  test('Ohne Berechtigung: Teaser mit Ablaufdatum und Upgrade-Link statt Formular, Upload bleibt', async ({ page }) => {
    await mockEntitlement(page, TRIAL_OVER);
    await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await openEudaTab(page);

    await expect(page.getByTestId('euda-teaser')).toBeVisible();
    await expect(page.getByTestId('euda-teaser')).toContainText('21.10.2026');
    await expect(page.getByTestId('euda-upgrade')).toHaveAttribute('href', '/upgrade');
    await expect(page.locator('#euda-password')).toHaveCount(0);
    await expect(page.getByText('Export-Datei (.json oder .zip)')).toBeVisible();
  });

  test('Im Trial: Schritt 1 mit Ablaufdatum, dann Formular', async ({ page }) => {
    await mockEntitlement(page, TRIAL);
    await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await openEudaTab(page);

    await expect(page.getByTestId('euda-trial-hint')).toContainText('21.10.2026');
    await page.getByTestId('euda-start').click();
    await expect(page.locator('#euda-password')).toBeVisible();
  });

  test('Abgelaufene Verbindung: pausiert, Upgrade-Link, Trennen bleibt, keine Historie', async ({ page }) => {
    await mockEntitlement(page, TRIAL_OVER);
    const carId = await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify([{ ...statusActive(carId.value), status: 'EXPIRED' }]),
    }));
    await openEudaTab(page);

    await expect(page.getByTestId('euda-health')).toBeVisible();
    await expect(page.getByTestId('euda-expired-hint')).toBeVisible();
    await expect(page.getByTestId('euda-upgrade')).toBeVisible();
    await expect(page.getByTestId('euda-disconnect')).toBeVisible();
    await expect(page.getByTestId('euda-history')).toHaveCount(0);
    await expect(page.getByTestId('euda-reactivate-smartcar')).toHaveCount(0);
  });

  test('VW-Group-Auto aktiv: Tab startet aufgeklappt in Schritt 1, Erklaerung auf Wunsch', async ({ page }) => {
    await mockEntitlement(page, TRIAL);
    await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await page.goto('/imports');

    await expect(page.getByText(UPLOAD_LABEL)).toBeVisible();
    // Schritt 1: Entscheidung ohne Formular, Erklaerung eingeklappt
    await expect(page.getByTestId('euda-step-decide')).toBeVisible();
    await expect(page.getByTestId('euda-trial-hint')).toBeVisible();
    await expect(page.locator('#euda-password')).toHaveCount(0);
    await expect(page.getByTestId('euda-explainer')).toHaveCount(0);
    await page.getByTestId('euda-details-toggle').click();
    const explainer = page.getByTestId('euda-explainer');
    await expect(explainer).toBeVisible();
    await expect(explainer).toContainText('Was der EU Data Act für dein Fahrzeug bedeutet');
    await expect(explainer.getByRole('link', { name: /EU-Data-Act-Portal/ })).toHaveAttribute('href', 'https://eu-data-act.drivesomethinggreater.com');
  });

  test('Anderes Auto aktiv: Tab startet zugeklappt', async ({ page }) => {
    await mockEntitlement(page, TRIAL);
    await page.route('**/api/cars', async route => {
      const response = await route.fetch();
      const cars = (await response.json() as { brand: string }[]).map(c => ({ ...c, brand: 'HYUNDAI' }));
      await route.fulfill({ response, body: JSON.stringify(cars) });
    });
    await page.goto('/imports');
    await page.getByRole('button', { name: /VW Gruppe \(EU Data Act\)/ }).waitFor();
    await expect(page.getByText(UPLOAD_LABEL)).toHaveCount(0);
  });

  test('VW-Group-Fahrzeug anlegen: Modal fuehrt in zwei Schritten zum Verbinden', async ({ page }) => {
    await mockEntitlement(page, TRIAL);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    // Das Anlegen selbst laeuft nicht gegen die DB - die Antwort ist ein Skoda.
    await page.route('**/api/cars', async route => {
      if (route.request().method() !== 'POST') return route.fallback();
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        car: { id: 'skoda-new', brand: 'SKODA', model: 'Enyaq', year: 2024, status: 'ACTIVE', effectiveBatteryCapacityKwh: 77 },
        coinsAwarded: 0,
      }) });
    });
    await page.goto('/cars');
    await page.getByRole('button', { name: /Fahrzeug hinzufügen/ }).first().click();

    const brand = page.locator('form select').first();
    const brandValue = await brand.locator('option').evaluateAll(o =>
      (o as HTMLOptionElement[]).map(x => x.value).find(v => /^(SKODA|VW|VOLKSWAGEN)$/i.test(v)) ?? '');
    expect(brandValue).not.toBe('');
    await brand.selectOption(brandValue);
    const model = page.locator('form select').nth(1);
    await expect(model).toBeEnabled();
    await model.selectOption({ index: 1 });
    const capacity = page.locator('form button[type="button"]').filter({ hasText: /kWh/ }).first();
    if (await capacity.isVisible().catch(() => false)) await capacity.click();
    await page.locator('form button[type="submit"]').first().click();

    const modal = page.getByTestId('euda-prompt');
    await expect(modal).toBeVisible();
    await expect(modal.getByTestId('euda-step-decide')).toBeVisible();
    await expect(modal.getByTestId('euda-trial-hint')).toBeVisible();
    await expect(modal.getByTestId('euda-start')).toBeVisible();
    await modal.getByTestId('euda-start').click();
    await expect(modal.getByTestId('euda-connect')).toBeVisible();
    await modal.getByRole('button', { name: 'Später einrichten' }).click();
    await expect(modal).toHaveCount(0);
  });

  test('Ohne VW-Group-Fahrzeug gibt es keine AutoSync-Karte, auch fuer Premium', async ({ page }) => {
    await mockEntitlement(page, PAID);
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
    await mockEntitlement(page, PAID);
    const carId = await useSkodaCars(page);
    let statusCalls = 0;
    await page.route('**/api/eu-data-act/status', route => {
      statusCalls++;
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await mockFreshActivity(page);
    let connectBody: Record<string, string> | null = null;
    await page.route('**/api/eu-data-act/cars/*/connect', route => {
      connectBody = route.request().postDataJSON() as Record<string, string>;
      const carId = route.request().url().match(/cars\/([^/]+)\/connect/)![1];
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(statusActive(carId)) });
    });
    // Fahrzeug haengt ueber Smartcar - nur dann gehoert der Dubletten-Hinweis ins Formular
    await page.route('**/api/smartcar/status', async route => {
      // Die Fahrzeug-ID kommt aus dem /api/cars-Aufruf, der parallel laufen kann - kurz darauf warten
      for (let i = 0; i < 50 && !carId.value; i++) await new Promise(r => setTimeout(r, 100));
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        connected: true, carId: carId.value, vehicleName: 'Enyaq', vin: null, vehicleState: null,
        lastCheckedAt: null, lastSoc: null, sessionActive: false, sessionStartedAt: null, sessionEnergyAdded: null,
      }) });
    });
    await openEudaTab(page);
    await page.getByTestId('euda-start').click();

    // Transparenz-Hinweise stehen im Formular, bevor der Nutzer etwas eingibt
    await expect(page.getByText(/nicht gespeichert/).first()).toBeVisible();
    await expect(page.getByText(/Datenanfrage an/)).toBeVisible();
    await expect(page.getByTestId('euda-smartcar-note')).toBeVisible();
    await expect(page.getByTestId('euda-open-source')).toHaveAttribute('href', /github\.com\/sebastianwien\/ev-monitor.*EudaLoginClient\.java$/);

    await page.getByRole('radio', { name: 'Škoda' }).click();
    await page.locator('#euda-email').fill('Max@Example.com');
    await page.locator('#euda-password').fill('geheim-123');
    await page.getByTestId('euda-connect').click();

    await expect(page.getByTestId('euda-success')).toBeVisible();
    await expect(page.getByText(WAITING_FIRST)).toBeVisible();
    await expect(page.getByText('max@example.com')).toBeVisible();
    expect(connectBody).toEqual({ brand: 'skoda', email: 'Max@Example.com', password: 'geheim-123' });
    expect(statusCalls).toBeGreaterThan(0);
    await expect(page.locator('#euda-password')).toHaveCount(0);
    await expect(page.getByTestId('euda-history')).toBeVisible();
    await expect(page.getByTestId('euda-disconnect')).toBeVisible();
  });

  test('Falsches Passwort zeigt den passenden Fehler, Formular bleibt', async ({ page }) => {
    await mockEntitlement(page, PAID);
    await useSkodaCars(page);
    await page.route('**/api/eu-data-act/status', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    // 422, nicht 401 - ein 401 wuerde der Axios-Interceptor als abgelaufene Sitzung deuten
    await page.route('**/api/eu-data-act/cars/*/connect', route => route.fulfill({
      status: 422, contentType: 'application/json',
      body: JSON.stringify({ code: 'INVALID_CREDENTIALS', message: 'E-Mail oder Passwort falsch' }),
    }));
    await openEudaTab(page);
    await page.getByTestId('euda-start').click();

    await page.locator('#euda-email').fill('max@example.com');
    await page.locator('#euda-password').fill('falsch');
    await page.getByTestId('euda-connect').click();

    await expect(page.getByRole('alert')).toContainText('E-Mail oder Passwort falsch');
    await expect(page.locator('#euda-password')).toHaveValue('');
  });

  test('Bestehende Verbindung: Status, Historie anfordern, Trennen', async ({ page }) => {
    await mockEntitlement(page, PAID);
    const carId = await useSkodaCars(page);
    let connected = true;
    await page.route('**/api/eu-data-act/status', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(connected ? [statusActive(carId.value)] : []),
    }));
    await mockFreshActivity(page);
    let historyRequested = false;
    await page.route('**/api/eu-data-act/cars/*/history', route => { historyRequested = true; return route.fulfill({ status: 202 }); });
    await page.route('**/api/eu-data-act/cars/*', route => {
      if (route.request().method() === 'DELETE') { connected = false; return route.fulfill({ status: 204 }); }
      return route.fallback();
    });
    await openEudaTab(page);

    await expect(page.getByText(WAITING_FIRST)).toBeVisible();
    await page.getByTestId('euda-history').click();
    // Der Stand steht in der Faktenzeile "Ladehistorie", der Knopf verschwindet
    await expect(page.getByText('angefordert, kommt in einigen Stunden')).toBeVisible();
    await expect(page.getByTestId('euda-history')).toHaveCount(0);
    expect(historyRequested).toBe(true);

    await page.getByTestId('euda-disconnect').click();
    await expect(page.getByTestId('euda-step-decide')).toBeVisible();
  });
});

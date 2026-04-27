import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe.configure({ mode: 'serial' });

async function login(page: any) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

test.describe('Ladevorgänge anlegen und bearbeiten', () => {
  test.beforeAll(async () => {
    // Sauberer Zustand: alle Logs des Testfahrzeugs löschen
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    const authResp = await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    });
    const { token } = await authResp.json();

    const carsResp = await api.get('/api/cars', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const cars = await carsResp.json();
    if (!cars.length) return;

    const carId = cars[0].id;
    const logsResp = await api.get(`/api/logs?carId=${carId}&limit=50`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const logs = await logsResp.json();
    const ids: string[] = logs.map((l: any) => l.id);

    if (ids.length > 0) {
      await api.delete('/api/logs/batch', {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: ids,
      });
    }
  });

  test.beforeEach(async ({ page }) => {
    // Vor dem ersten Seitenaufruf setzen damit Vue beim Mount schon "alles gesehen" liest
    const allKeys = featureAnnouncements.map(a => a.key);
    await page.addInitScript((keys: string[]) => {
      localStorage.setItem('seen-announcements', JSON.stringify(keys));
    }, allKeys);

    await login(page);
  });

  test('Ladevorgang mit Pflichtfeldern erfassen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    await page.locator('input[placeholder="z.B. 42.5"]').fill('35.5');
    await page.locator('input[placeholder="z.B. 12.50"]').fill('8.90');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang mit "kWh laut Fahrzeug" (Auto-Modus) erfassen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    // Auf "Auto"-Modus (kwhAtVehicle) umschalten
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();

    // kWh im Auto-Modus eintragen (schreibt in kwhAtVehicle)
    await page.locator('input[placeholder="z.B. 42.5"]').fill('37.5');
    await page.locator('input[placeholder="z.B. 12.50"]').fill('10.00');

    // Eindeutiger Zeitstempel um Duplikat-Kollision mit Test 1 zu vermeiden
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    await page.locator('input[type="datetime-local"]').fill(yesterday.toISOString().slice(0, 16));

    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang bearbeiten - auf kWh laut Fahrzeug umstellen', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    const editButton = page.locator('[title="Ladevorgang bearbeiten"]').first();
    await expect(editButton).toBeVisible({ timeout: 10_000 });
    await editButton.click();

    // Modal offen warten
    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).toBeVisible({ timeout: 5_000 });

    // Auf "Auto"-Modus umschalten und Wert eintragen
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();
    await page.locator('input[placeholder="z.B. 42.5"]').fill('36.0');
    await page.locator('button:has-text("Speichern")').click();

    // Modal muss schliessen
    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).not.toBeVisible({ timeout: 5_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang bearbeiten - Beide Felder gesetzt: Charger-Modus default, Netto per Toggle erreichbar', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    // Test 3 hat Vehicle-Wert (36) gesetzt, kwhCharged (35.5) bleibt erhalten - beide Felder gesetzt
    const editButton = page.locator('[title="Ladevorgang bearbeiten"]').first();
    await expect(editButton).toBeVisible({ timeout: 10_000 });
    await editButton.click();

    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).toBeVisible({ timeout: 5_000 });

    // Beide Felder gesetzt -> Charger-Modus hat Prio, kwhCharged=35.5 sichtbar
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).not.toBeVisible({ timeout: 3_000 });
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('35.5');

    // Nach Toggle auf Vehicle: Netto-Wert=36 sichtbar
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).toBeVisible();
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('36');

    await page.locator('button:has-text("Abbrechen")').click();
    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).not.toBeVisible({ timeout: 3_000 });
    expect(errors).toEqual([]);
  });

  test('Ladevorgang bearbeiten - Brutto-Wert aktualisieren wenn beide Felder gesetzt', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    // Log hat kwhCharged=35.5 und kwhAtVehicle=36 - Charger-Modus ist Default
    const editButton = page.locator('[title="Ladevorgang bearbeiten"]').first();
    await expect(editButton).toBeVisible({ timeout: 10_000 });
    await editButton.click();

    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).toBeVisible({ timeout: 5_000 });

    // Charger-Modus weil kwhCharged Prio hat
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).not.toBeVisible({ timeout: 3_000 });
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('35.5');

    // Brutto-Wert auf 38 aktualisieren und speichern
    await page.locator('input[placeholder="z.B. 42.5"]').fill('38.0');
    await page.locator('button:has-text("Speichern")').click();

    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).not.toBeVisible({ timeout: 5_000 });

    // Nochmal oeffnen: Charger=38, Vehicle=36 (beide erhalten)
    await editButton.click();
    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).toBeVisible({ timeout: 5_000 });

    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).not.toBeVisible();
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('38');

    await page.locator('button:has-text("Abbrechen")').click();
    expect(errors).toEqual([]);
  });

  test('kWh Toggle: Brutto eingeben, auf Netto wechseln - kein Carry-Over, Feld leer', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    await page.locator('input[placeholder="z.B. 42.5"]').fill('50.0');
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();

    // Kein Carry-Over: Feld muss leer sein
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('');
    // Vehicle-Hint sichtbar
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).toBeVisible();

    expect(errors).toEqual([]);
  });

  test('kWh Toggle: Beide Werte eingeben - Payload enthält kwhCharged und kwhAtVehicle', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    let capturedPayload: Record<string, unknown> | null = null;
    await page.route('**/api/logs', async route => {
      if (route.request().method() === 'POST') {
        capturedPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.abort(); // Log nicht wirklich anlegen - kein Einfluss auf spätere Edit-Tests
      } else {
        await route.continue();
      }
    });

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    // Brutto eingeben
    await page.locator('input[placeholder="z.B. 42.5"]').fill('50.0');

    // Auf Netto wechseln und Netto eingeben
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();
    await page.locator('input[placeholder="z.B. 42.5"]').fill('47.0');

    await page.locator('input[placeholder="z.B. 12.50"]').fill('12.00');
    await page.locator('button[type="submit"]').click();

    // Payload muss beide Felder enthalten
    expect(capturedPayload).not.toBeNull();
    expect(capturedPayload!['kwhCharged']).toBeCloseTo(50.0, 1);
    expect(capturedPayload!['kwhAtVehicle']).toBeCloseTo(47.0, 1);

    expect(errors).toEqual([]);
  });

  test('kWh Toggle: Zurückwechseln zeigt ursprünglichen Brutto-Wert', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.goto('/erfassen');
    await page.waitForLoadState('networkidle');

    // Brutto eingeben
    await page.locator('input[placeholder="z.B. 42.5"]').fill('50.0');

    // Auf Netto wechseln und Netto-Wert eingeben
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();
    await page.locator('input[placeholder="z.B. 42.5"]').fill('47.0');

    // Zurück auf Brutto - muss 50 zeigen, nicht 47
    await page.locator('[data-testid="kwh-mode-charger"]').click();
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('50');

    expect(errors).toEqual([]);
  });

  test('Edit: Beide Felder gesetzt - Charger-Modus default, nach Switch Netto-Wert sichtbar', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));

    await page.waitForLoadState('networkidle');

    // Nach Test 5 hat der erste Log kwhCharged=38 UND kwhAtVehicle=36
    const editButton = page.locator('[title="Ladevorgang bearbeiten"]').first();
    await expect(editButton).toBeVisible({ timeout: 10_000 });
    await editButton.click();

    await expect(page.locator('h2:has-text("Ladevorgang bearbeiten")')).toBeVisible({ timeout: 5_000 });

    // Charger-Modus weil kwhCharged Prio hat - Wert = 38
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).not.toBeVisible({ timeout: 3_000 });
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('38');

    // Auf Netto wechseln - muss 36 zeigen (nicht leer, Wert liegt vor)
    await page.locator('[data-testid="kwh-mode-vehicle"]').click();
    await expect(page.locator('text=Netto-kWh die dein Akku aufgenommen hat')).toBeVisible();
    await expect(page.locator('input[placeholder="z.B. 42.5"]')).toHaveValue('36');

    await page.locator('button:has-text("Abbrechen")').click();
    expect(errors).toEqual([]);
  });
});

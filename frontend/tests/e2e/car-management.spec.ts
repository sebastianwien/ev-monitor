import { test, expect, request, type Page } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Auto-Anlegen seit A2: nur mit Akku-Zeile aus der Liste, keine freie Kapazität mehr.
 * Fehlt etwas, öffnet der Link "Mein Akku ist nicht dabei?" den Meldedialog.
 */
async function dismissAnnouncements(page: Page) {
  const keys = featureAnnouncements.map(a => a.key);
  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
  }, keys);
}

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

/** Fahrzeugformular: im Sheet, sobald der Nutzer schon ein Auto hat, sonst inline im Leerzustand. */
function carForm(page: Page) {
  return page.getByTestId('car-form-sheet').locator('form').or(page.locator('form').first()).first();
}

/** Marke und Modell wählen, dann die erste wählbare Akku-Zeile (Trim-Gruppe plus Variante oder flache Liste). */
async function pickModel3WithBattery(page: Page) {
  const form = carForm(page);
  const selects = form.locator('select');
  await selects.nth(0).selectOption('TESLA');
  await selects.nth(1).selectOption('MODEL_3');

  const api = await request.newContext({ baseURL: API_URL });
  const models = await (await api.get('/api/cars/brands/TESLA/models')).json();
  const capacities: { trimLevel: string | null }[] = models.find((m: any) => m.value === 'MODEL_3').capacities;
  const trimGroups = [...new Set(capacities.map(c => c.trimLevel).filter(Boolean))];

  const capacityArea = form.locator('div.md\\:col-span-2').filter({ hasText: 'Ausführung' }).first();
  const pick = capacityArea.locator('button').filter({ hasNotText: 'nicht dabei' });
  await pick.first().click();
  if (trimGroups.length > 0) {
    // Erste Trim-Gruppe ist gewählt; falls mehrere Varianten erscheinen, die erste nehmen.
    const variants = pick.nth(trimGroups.length);
    if (await variants.count()) await variants.click();
  }
}

test.describe('Auto anlegen', () => {
  test.beforeEach(async ({ page }) => {
    await dismissAnnouncements(page);
    await login(page);
    await page.goto('/cars');
    await page.waitForLoadState('networkidle');
  });

  test('legt ein Auto mit Akku-Zeile an und löscht es wieder', async ({ page }) => {
    await page.getByRole('button', { name: '+ Fahrzeug hinzufügen' }).click();
    const form = carForm(page);
    await expect(form).toBeVisible();
    await pickModel3WithBattery(page);

    const plate = `E2E-${Date.now().toString().slice(-6)}`;
    await form.locator('select').nth(2).selectOption({ index: 1 });
    await form.locator('input[type="number"]').first().fill('2023');
    await form.getByPlaceholder('z.B. M-EV 123').fill(plate);
    await form.locator('button[type="submit"]').click();

    // Das neue Auto ist danach das gewählte: Kennzeichen steht im Hero.
    await expect(page.locator('[data-testid^="car-hero-"]').getByText(plate)).toBeVisible({ timeout: 10_000 });

    // Aufräumen über die API, damit der Testnutzer nicht mit jedem Lauf ein Auto mehr hat.
    const token = await page.evaluate(() => localStorage.getItem('token'));
    const api = await request.newContext({ baseURL: API_URL, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
    const cars = await (await api.get('/api/cars')).json();
    const created = cars.find((c: any) => c.licensePlate === plate);
    expect(created?.vehicleSpecificationId, 'neues Auto hängt an einer Spec-Zeile').toBeTruthy();
    await api.delete(`/api/cars/${created.id}`);
  });

  test('"Mein Akku ist nicht dabei?" öffnet den Meldedialog mit vorbelegter Mail', async ({ page }) => {
    await page.getByRole('button', { name: '+ Fahrzeug hinzufügen' }).click();
    const form = carForm(page);
    await form.locator('select').nth(0).selectOption('TESLA');
    await form.locator('select').nth(1).selectOption('MODEL_3');

    await page.getByRole('button', { name: 'Mein Akku ist nicht dabei?' }).click();

    const dialog = page.getByTestId('missing-vehicle-dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('input[type="email"]')).toHaveValue(TEST_USER.email);
    await expect(dialog.locator('textarea')).toHaveAttribute('maxlength', '150');
  });

  test('Marke "Andere Marke" ist beim Anlegen nicht wählbar', async ({ page }) => {
    await page.getByRole('button', { name: '+ Fahrzeug hinzufügen' }).click();
    const options = await carForm(page).locator('select').nth(0).locator('option').allTextContents();
    expect(options.join('|')).not.toContain('Andere Marke');
  });
});

test.describe('Fahrzeugseite', () => {
  test.beforeEach(async ({ page }) => {
    await dismissAnnouncements(page);
    await login(page);
    await page.goto('/cars');
    await page.waitForLoadState('networkidle');
  });

  test('Fixkosten erfassen zeigt die Jahressumme in der Kostenkarte', async ({ page }) => {
    const card = page.getByTestId('car-cost-card');
    await expect(card).toBeVisible();
    await card.getByTestId('car-cost-add').click();

    const sheet = page.getByTestId('fixed-cost-sheet');
    await expect(sheet).toBeVisible();
    const description = `E2E Versicherung ${Date.now().toString().slice(-5)}`;
    await sheet.locator('input').nth(0).fill(description);
    await sheet.locator('input[type="number"]').first().fill('1234');
    await sheet.locator('select').nth(0).selectOption('INSURANCE');
    await sheet.locator('select').nth(1).selectOption('ONE_TIME');
    await sheet.locator('input[type="date"]').first().fill(new Date().toISOString().slice(0, 10));
    await sheet.getByRole('button', { name: 'Speichern' }).click();

    await expect(sheet).toBeHidden({ timeout: 10_000 });
    await expect(card.getByRole('button', { name: /Versicherung/ })).toBeVisible();
    await card.getByRole('button', { name: /Versicherung/ }).click();
    await expect(card.getByText(description)).toBeVisible();

    // Aufräumen über die API
    const token = await page.evaluate(() => localStorage.getItem('token'));
    const api = await request.newContext({ baseURL: API_URL, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
    const cars = await (await api.get('/api/cars')).json();
    for (const car of cars) {
      const items = await (await api.get(`/api/fixed-costs?carId=${car.id}`)).json();
      for (const item of items.filter((i: any) => i.description === description)) await api.delete(`/api/fixed-costs/${item.id}`);
    }
  });

  test('Mehr-Menü öffnet das Bearbeiten-Sheet', async ({ page }) => {
    await page.getByTestId('car-menu-btn').first().click();
    await page.getByTestId('car-menu-edit').click();
    await expect(page.getByTestId('car-form-sheet')).toBeVisible();
    await expect(page.getByTestId('car-form-sheet').locator('select').nth(0)).toHaveValue('TESLA');
  });
});

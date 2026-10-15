import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Fahrzeugbewertung mobil (390 px): ein Formular mit Pflicht- und optionalem Teil, ein Absenden, Ergebnis mit Watt.
 * Jeder Lauf legt ein eigenes Auto mit fünf Ladungen an (eine Bewertung je Auto) und löscht es danach.
 */
test.describe('Fahrzeug bewerten', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  let token = '';
  let carId = '';

  test.beforeEach(async ({ page }) => {
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    token = (await (await api.post('/api/auth/login', {
      data: { email: TEST_USER.email, password: TEST_USER.password },
    })).json()).token;
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
    const car = await api.post('/api/cars', {
      headers,
      data: { model: 'ID_3', year: 2023, licensePlate: '', trim: null, customNetCapacityKwh: 58, powerKw: 150,
        batteryDegradationPercent: null, hasHeatPump: false, vehicleSpecificationId: null },
    });
    expect(car.ok()).toBeTruthy();
    carId = (await car.json()).car.id;
    const minute = Math.floor(Math.random() * 60 * 24);
    for (let day = 1; day <= 5; day++) {
      const loggedAt = new Date(Date.UTC(2021, 2, day, Math.floor(minute / 60), minute % 60));
      const log = await api.post('/api/logs', {
        headers,
        data: { carId, kwhCharged: 30, costEur: 9, odometerKm: 10_000 + day * 250, chargingType: 'AC',
          loggedAt: loggedAt.toISOString().slice(0, 19) },
      });
      expect(log.ok()).toBeTruthy();
    }
    await api.dispose();

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

  test.afterEach(async () => {
    const api = await playwrightRequest.newContext({ baseURL: API_URL });
    await api.delete(`/api/cars/${carId}`, { headers: { Authorization: `Bearer ${token}` } });
    await api.dispose();
  });

  const stars = (page: import('@playwright/test').Page, question: string, value: number) =>
    page.getByRole('radiogroup', { name: question }).getByRole('radio').nth(value - 1).click();

  test('Ein Formular: Pflichtteil, optionaler Teil mit Text im Mittelpunkt, ein Absenden, dann Ergebnis mit Watt', async ({ page }) => {
    await page.goto(`/cars/${carId}/bewerten?src=cars`);

    const submit = page.getByRole('button', { name: /^(Noch \d|Absenden)$/ });
    await expect(submit).toHaveText('Noch 7', { timeout: 10_000 });
    await expect(page.getByTestId('badge-required')).toHaveText('Pflicht');
    await expect(page.getByTestId('badge-optional')).toHaveText('Optional');
    await expect(page.getByRole('heading', { name: 'Deine Erfahrung mit dem ID.3' })).toBeVisible();

    await page.getByRole('radio', { name: 'Ja, genau diesen' }).click();
    await stars(page, 'Passt er zu deinem Alltag?', 5);
    await stars(page, 'Wie zufrieden bist du mit Software und Bedienung?', 4);
    await page.getByRole('button', { name: 'Kann ich nicht beurteilen' }).first().click();
    await stars(page, 'Wie gut sind Verarbeitung und Komfort?', 4);
    await page.getByRole('button', { name: 'Kann ich nicht beurteilen' }).last().click();
    await page.getByRole('radio', { name: 'Wie erwartet' }).click();
    await expect(submit).toHaveText('Absenden');

    await page.getByRole('group', { name: 'Was überzeugt dich am meisten?' }).getByRole('button', { name: 'Platz' }).click();
    await page.getByLabel('Dein Text').fill('Im Alltag sehr entspannt, auf der Autobahn im Winter eher 280 km.');
    await expect(page.getByText(/Erscheint als Zitat/)).toBeVisible();
    await submit.click();

    await expect(page.getByText('+50 Watt gutgeschrieben')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/würden? ihn wieder kaufen/)).toBeVisible();
    await expect(page.getByText('Deinen Text lesen wir in den nächsten Tagen.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Text ergänzen' })).toHaveCount(0);
  });

  test('/cars zeigt den Teaser über dem Auto, nach Wegklicken die Zeile', async ({ page }) => {
    await page.goto('/cars');
    const card = page.getByTestId(`car-card-${carId}`);
    const teaser = card.getByTestId('rating-teaser');
    await expect(teaser).toContainText('Bewerte deinen', { timeout: 10_000 });
    await expect(card.getByTestId('car-rating-row')).toHaveCount(0);
    await teaser.getByRole('button', { name: 'Hinweis schließen' }).click();
    await expect(teaser).toHaveCount(0);
    await expect(card.getByTestId('car-rating-row')).toContainText('Bewerten');
  });

  test('Lösch-Flow: erst bewerten, dann das Auto löschen', async ({ page }) => {
    await page.goto('/cars');
    const card = page.getByTestId(`car-card-${carId}`);
    await expect(card.getByTestId('rating-teaser')).toBeVisible({ timeout: 10_000 });

    await card.getByRole('button', { name: 'Löschen', exact: true }).click();
    const sheet = page.getByTestId('rating-delete-sheet');
    await expect(sheet).toContainText('Magst du ihn kurz bewerten?');
    await sheet.getByRole('button', { name: 'Bewerten' }).click();
    await expect(page).toHaveURL(/\/bewerten\?src=loeschen&danach=loeschen/);

    await page.getByRole('radio', { name: 'Nein' }).click();
    for (const question of ['Passt er zu deinem Alltag?', 'Wie zufrieden bist du mit Software und Bedienung?', 'Wie gut sind Verarbeitung und Komfort?']) {
      await stars(page, question, 2);
    }
    await page.getByRole('button', { name: 'Kann ich nicht beurteilen' }).first().click();
    await page.getByRole('button', { name: 'Kann ich nicht beurteilen' }).last().click();
    await page.getByRole('radio', { name: 'Teurer als erwartet' }).click();
    await page.getByRole('button', { name: 'Absenden' }).click();

    // Zurück zu /cars, dort fragt das Löschen wie gewohnt nach
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('link', { name: 'Weiter zum Löschen' }).click();
    await expect(page).toHaveURL(/\/cars$/);
    await expect(page.getByTestId(`car-card-${carId}`)).toHaveCount(0, { timeout: 10_000 });
  });
});

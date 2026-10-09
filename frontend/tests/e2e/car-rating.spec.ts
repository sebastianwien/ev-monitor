import { test, expect, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Fahrzeugbewertung mobil (390 px): sieben Kernfragen, Ergebnis mit Watt, Vertiefung mit Chip und Text.
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

  test('Kernfragen, Ergebnis mit Watt, Vertiefung mit Text', async ({ page }) => {
    await page.goto(`/cars/${carId}/bewerten?src=e2e`);

    const submit = page.getByRole('button', { name: /^(Noch \d|Absenden)$/ });
    await expect(submit).toHaveText('Noch 7', { timeout: 10_000 });
    await expect(submit).toBeDisabled();

    await page.getByRole('radio', { name: 'Ja, genau diesen' }).click();
    await stars(page, 'Passt er zu deinem Alltag?', 4);
    await expect(page.getByText('Passt gut')).toBeVisible();
    await page.getByRole('button', { name: 'Fahre ich selten Langstrecke' }).click();
    await stars(page, 'Wie zufrieden bist du mit Software und Bedienung?', 3);
    await stars(page, 'Wie gut sind Verarbeitung und Komfort?', 5);
    await page.getByRole('button', { name: 'Noch nicht gebraucht' }).click();
    await expect(submit).toHaveText('Noch 1');
    await page.getByRole('radio', { name: 'Wie erwartet' }).click();

    await expect(submit).toHaveText('Absenden');
    await submit.click();

    await expect(page.getByText('+50 Watt gutgeschrieben')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/würden? ihn wieder kaufen/)).toBeVisible();

    await page.getByRole('button', { name: 'Stärken, Schwächen und deine Erfahrung' }).click();
    await page.getByRole('group', { name: 'Was überzeugt dich am meisten?' }).getByRole('button', { name: 'Platz' }).click();
    await page.getByLabel(/Deine Erfahrung/).fill('Im Alltag sehr entspannt, auf der Autobahn im Winter eher 280 km.');
    await expect(page.getByText(/Erscheint als Zitat/)).toBeVisible();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByRole('heading', { name: /Dein Urteil zählt ab jetzt/ })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Deinen Text lesen wir in den nächsten Tagen.')).toBeVisible();
  });

  test('Lösch-Flow: erst bewerten, dann das Auto löschen', async ({ page }) => {
    await page.goto('/cars');
    const card = page.getByTestId(`car-card-${carId}`);
    await expect(card.getByTestId('car-rating-row')).toContainText('Bewerten', { timeout: 10_000 });

    await card.getByRole('button', { name: 'Löschen', exact: true }).click();
    const sheet = page.getByTestId('rating-delete-sheet');
    await expect(sheet).toContainText('Magst du ihn kurz bewerten?');
    await sheet.getByRole('button', { name: 'Bewerten' }).click();
    await expect(page).toHaveURL(/\/bewerten\?src=loeschen&danach=loeschen/);

    await page.getByRole('radio', { name: 'Nein' }).click();
    for (const question of ['Passt er zu deinem Alltag?', 'Wie zufrieden bist du mit Software und Bedienung?', 'Wie gut sind Verarbeitung und Komfort?']) {
      await stars(page, question, 2);
    }
    await page.getByRole('button', { name: 'Fahre ich selten Langstrecke' }).click();
    await page.getByRole('button', { name: 'Noch nicht gebraucht' }).click();
    await page.getByRole('radio', { name: 'Teurer als erwartet' }).click();
    await page.getByRole('button', { name: 'Absenden' }).click();

    // Zurück zu /cars, dort fragt das Löschen wie gewohnt nach
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Weiter zum Löschen' }).click();
    await expect(page).toHaveURL(/\/cars$/);
    await expect(page.getByTestId(`car-card-${carId}`)).toHaveCount(0, { timeout: 10_000 });
  });
});

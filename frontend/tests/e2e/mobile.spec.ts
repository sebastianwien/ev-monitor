import { test, expect, devices, request as playwrightRequest } from '@playwright/test';
import { TEST_USER } from './global-setup';
import { featureAnnouncements } from '../../src/config/featureAnnouncements';

// iPhone 12 viewport direkt pro Test setzen (test.use in describe geht nicht mit defaultBrowserType)
const iphone12 = devices['iPhone 12'];

const API_URL = process.env.API_URL || 'http://localhost:8080';

/**
 * Legt per API einen Ladevorgang an. Alle Specs teilen sich denselben Testuser und
 * log-management.spec.ts loescht in seinem beforeAll saemtliche Logs des Testfahrzeugs.
 * Bei fullyParallel laeuft das gleichzeitig zu diesen Tests - wer sich auf vorhandene
 * Logs verlaesst, wird sporadisch rot. Deshalb legen die Log-abhaengigen Tests ihre
 * Daten unmittelbar vor Gebrauch selbst an.
 */
async function createLog() {
  const api = await playwrightRequest.newContext({ baseURL: API_URL });
  const authResp = await api.post('/api/auth/login', {
    data: { email: TEST_USER.email, password: TEST_USER.password },
  });
  const { token } = await authResp.json();
  const headers = { Authorization: `Bearer ${token}` };

  const cars = await (await api.get('/api/cars', { headers })).json();
  if (!Array.isArray(cars) || cars.length === 0) throw new Error('[E2E] Kein Testfahrzeug vorhanden');

  // Eindeutiger Zeitstempel in der Vergangenheit: der Server lehnt zwei Logs mit
  // gleicher Zeit am selben Fahrzeug mit 409 ab. "Jetzt" kollidiert sowohl mit dem
  // parallelen zweiten Mobile-Test als auch mit den Logs, die log-management.spec.ts
  // ueber das Formular anlegt.
  const offsetMinutes = Math.floor(Math.random() * 500_000);
  const loggedAt = new Date(Date.UTC(2024, 0, 1) + offsetMinutes * 60_000)
    .toISOString().slice(0, 19);

  const resp = await api.post('/api/logs', {
    headers,
    data: {
      carId: cars[0].id,
      kwhCharged: 45.5,
      costEur: 18.2,
      odometerKm: 5000 + Math.floor(offsetMinutes / 100),
      socAfterChargePercent: 80,
      socBeforeChargePercent: 20,
      chargingType: 'AC',
      loggedAt,
    },
  });
  if (!resp.ok()) throw new Error(`[E2E] Log-Anlage fehlgeschlagen: ${resp.status()} ${await resp.text()}`);
  await api.dispose();
}

test('Mobile: Landing Page vollständig nutzbar', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/');

  const h1 = page.locator('h1').first();
  await expect(h1).toBeVisible();

  const cta = page.locator('a[href*="/modelle"]').first();
  await expect(cta).toBeVisible();
  const box = await cta.boundingBox();
  expect(box).not.toBeNull();
  // CTA darf nicht ausserhalb des Viewports rechts abgeschnitten sein (iPhone 12: 390px)
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);
  await context.close();
});

test('Mobile: /modelle zeigt Modell-Cards ohne Layout-Bruch', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/modelle');

  const firstCard = page.locator('a[href*="/modelle/"]').first();
  await expect(firstCard).toBeVisible({ timeout: 10_000 });

  const box = await firstCard.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);
  await context.close();
});

test('Mobile: Login-Formular bedienbar', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();
  await page.goto('/login');

  await expect(page.locator('input[type="text"]')).toBeVisible();
  await expect(page.locator('input[type="password"]')).toBeVisible();
  await expect(page.locator('button[type="submit"]')).toBeVisible();

  await page.locator('input[type="text"]').tap();
  await page.locator('input[type="text"]').fill('test@test.de');
  await expect(page.locator('input[type="text"]')).toHaveValue('test@test.de');
  await context.close();
});

test('Mobile: Edit-Modal auf /logs liegt im Viewport (nicht im Pager-Track)', async ({ browser }) => {
  await createLog();
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();

  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
  }, featureAnnouncements.map(a => a.key));

  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });

  await page.goto('/logs');
  await page.waitForLoadState('networkidle');

  // Mobile Karten sind eingeklappt - das Aktionsmenue liegt in der aufgeklappten Karte.
  await page.locator('button[aria-expanded]:has-text("kWh"):visible').first().click();

  // :visible grenzt auf die aktive Pane ein - der Pager haelt die Dashboard-Pane
  // (mit denselben Aktionsmenues) zusammengeklappt im DOM.
  const menuButton = page.locator('[aria-label="Aktionen"]:visible').first();
  await expect(menuButton).toBeVisible({ timeout: 10_000 });
  await menuButton.click();
  await page.locator('button[role="menuitem"]:has-text("Bearbeiten"):visible').first().click();

  // Der Log-Feed liegt auf Mobile im SwipeTabPager - dessen Track traegt dauerhaft ein
  // translateX() und wird damit zum Containing Block fuer position:fixed. Ein nicht
  // teleportiertes Modal richtet sich dann am 200% breiten Track statt am Viewport aus
  // und haengt links aus dem Bild. Panel muss vollstaendig im Viewport liegen (390px).
  const panel = page.locator('[data-testid="edit-log-modal"]');
  await expect(panel).toBeVisible({ timeout: 5_000 });
  const box = await panel.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);

  await context.close();
});

test('Mobile: Kachel "Letzter Ladevorgang" oeffnet den Editor auf dem Dashboard', async ({ browser }) => {
  await createLog();
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();

  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
  }, featureAnnouncements.map(a => a.key));

  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
  await page.waitForLoadState('networkidle');

  // Die Kachel fuehrte frueher nur nach /logs - jetzt bearbeitet sie den Eintrag direkt.
  await page.locator('[data-testid="recent-charge-tile"]').click();

  const panel = page.locator('[data-testid="edit-log-modal"]');
  await expect(panel).toBeVisible({ timeout: 5_000 });
  await expect(page).toHaveURL(/\/dashboard/);

  // Das Modal liegt am Viewport, nicht am transformierten Pager-Track (iPhone 12: 390px).
  const box = await panel.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);

  await context.close();
});

test('Mobile: Auto-Edit-Modal auf /cars liegt im Viewport (nicht im Pager-Track)', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();

  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
  }, featureAnnouncements.map(a => a.key));

  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });

  await page.goto('/cars');
  await page.waitForLoadState('networkidle');

  // Fahrzeuge liegen im CarsLayout-Pager - derselbe transformierte Track wie beim Log-Feed.
  const editButton = page.locator('button:has-text("Bearbeiten"):visible').first();
  await expect(editButton).toBeVisible({ timeout: 10_000 });
  await editButton.click();

  const panel = page.locator('[data-testid="edit-car-modal"]');
  await expect(panel).toBeVisible({ timeout: 5_000 });
  const box = await panel.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(391);

  await context.close();
});

test('Mobile: Bottom-Nav wechselt Dashboard <-> Log-Feed, kein doppelter Tab-Switch', async ({ browser }) => {
  const context = await browser.newContext({ ...iphone12 });
  const page = await context.newPage();

  // Feature-Announcement-Modals wegklicken, sonst blockt der Overlay die Tab-Klicks
  await page.addInitScript((seenKeys: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(seenKeys));
  }, featureAnnouncements.map(a => a.key));

  await page.goto('/login');
  await page.locator('input[type="text"]').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
  // Auto-Card + Dashboard-Daten settlen lassen, sonst schiebt der ladende Header die Tabs
  await page.waitForLoadState('networkidle');

  // Auf Mobile fuehrt nur die Bottom-Nav zwischen Dashboard und Log-Feed - der frueher
  // im Header sitzende Segmented Control war dieselbe Navigation ein zweites Mal.
  // (Die Desktop-Workspace-Leiste liegt via `hidden md:block` im DOM, ist aber
  // display:none - deshalb :visible.)
  await expect(page.locator('a[role="tab"][href="/dashboard"]:visible')).toHaveCount(0);
  await expect(page.locator('a[role="tab"][href="/logs"]:visible')).toHaveCount(0);

  const startTab = page.locator('nav.bottom-nav a[href="/dashboard"]');
  const logsTab = page.locator('nav.bottom-nav a[href="/logs"]');
  await expect(startTab).toHaveAttribute('aria-current', 'page');

  // Auf Log-Feed umschalten: URL + aktiver Body wechseln, Auto-Card im Header bleibt
  await logsTab.click();
  await expect(page).toHaveURL(/\/logs/, { timeout: 5_000 });
  await expect(logsTab).toHaveAttribute('aria-current', 'page');
  await expect(startTab).not.toHaveAttribute('aria-current', 'page');

  // Zurueck auf Uebersicht
  await startTab.click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 5_000 });
  await expect(startTab).toHaveAttribute('aria-current', 'page');

  await context.close();
});

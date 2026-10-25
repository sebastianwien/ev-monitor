import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, log, type StoryData } from './fixtures/story';

/**
 * Ladekarte "Wo du lädst" im Story-Sheet: Orte nach Geld, Preisklassen gegen andere Fahrer,
 * Zeitraum, Fahrtlinien, Antippen mit Zoom und Rückweg. Orte, Vergleiche und Fahrten sind
 * gemockt, Kacheln kommen als leeres PNG (Fixture), nichts geht nach außen.
 */

const place = (over: Record<string, unknown>) => ({
  geohash: 'u1x3k7q', kind: 'PUBLIC', charges: 2, kwh: 80, costEur: 30, pricePerKwh: 0.375,
  lastChargedAt: '2026-10-05T10:00:00', lastLogId: '00000000-0000-0000-0000-0000000c0001',
  cpoName: 'Tesla Supercharger Hamburg, Germany - Stillhorn', chargingType: 'DC', ...over,
});

const PLACES = [
  place({ geohash: 'u1x3k7q', costEur: 30, pricePerKwh: 0.49 }),
  place({ geohash: 'u1x0yz', kind: 'HOME', cpoName: null, chargingType: 'AC', costEur: 12, pricePerKwh: 0.3 }),
  place({ geohash: 'u1qmb9q', cpoName: 'IONITY', costEur: 55, pricePerKwh: 0.52 }),
  place({ geohash: 'u33df5w', kind: 'UNKNOWN', cpoName: null, chargingType: 'AC', costEur: 0, pricePerKwh: null }),
  place({ geohash: 'u1x0hpe', cpoName: null, costEur: 8, pricePerKwh: 0.44, lastLogId: '00000000-0000-0000-0000-0000000c0009' }),
];
/** Oktober (Monat der Story am 14.10.): nur Zuhause und IONITY. */
const OCTOBER = [PLACES[1], PLACES[2]];
// Drei Stützpunkte in Hamburg (53,55/10,00 bis 53,57/10,05)
const LINE = 'o~yeI_c`|@o}@ozDo}@_|B';

async function openPlaces(page: Page, data: StoryData = { ...calmMonth(), places: PLACES }) {
  await openStory(page, data);
  // Nach openStory registriert: gewinnt gegen die Fixture, die Story selbst hat schon geladen.
  await page.route(/\/api\/charging-benchmarks\?/, route => {
    const ionity = new URL(route.request().url()).searchParams.get('cpo') === 'IONITY';
    const median = ionity ? 0.52 : 0.39;
    return route.fulfill({ json: {
      cpoName: ionity ? 'IONITY' : 'Tesla Supercharger', chargingType: 'DC', drivers: 15, medianPricePerKwh: median,
      lowerQuartilePricePerKwh: median - 0.04, upperQuartilePricePerKwh: median + 0.04, cards: [],
    } });
  });
  await page.route(/\/api\/logs\/places\?/, route => {
    const from = new URL(route.request().url()).searchParams.get('from');
    return route.fulfill({ json: from === '2026-10-01' ? OCTOBER : data.places });
  });
  await page.route(/\/api\/trips\?/, route => route.fulfill({ json: [
    { id: 't1', tripStartedAt: '2026-10-02T08:00:00Z', distanceKm: 12, tracePolyline: LINE },
    { id: 't2', tripStartedAt: '2026-10-03T08:00:00Z', distanceKm: 30, routePolyline: LINE, routeKind: 'MATCHED' },
    { id: 't3', tripStartedAt: '2026-10-04T08:00:00Z', distanceKm: 8, routePolyline: LINE, routeKind: 'SKETCH' },
  ] }));
  await page.route(/\/api\/logs\/00000000-0000-0000-0000-0000000c0009$/, route => route.fulfill({ json: {
    ...log(data.now, { id: '00000000-0000-0000-0000-0000000c0009', isPublicCharging: true, chargingType: 'DC' }), geohash: 'u1x3k7q',
  } }));
  await page.route(/\/api\/charging-provider-tariffs\/cpos\/nearby-stations/, route => route.fulfill({ json: [] }));
  await page.getByTestId('story-map-open').click();
  await expect(page.getByTestId('places-sheet')).toBeVisible();
}

/** Zoomstufe der vorderen Kachelebene, aus der Kachel-URL (/{z}/{x}/{y}.png). */
const tileZoom = (page: Page) => page.evaluate(() => {
  const levels = [...document.querySelectorAll<HTMLElement>('.places-map .leaflet-tile-container')]
    .filter(c => c.querySelector('img'))
    .sort((a, b) => Number(b.style.zIndex) - Number(a.style.zIndex));
  const src = levels[0]?.querySelector('img')?.getAttribute('src');
  return src ? Number(new URL(src).pathname.split('/')[1]) : null;
});

const names = (page: Page) => page.getByTestId('places-name').allTextContents();

test.describe('Story-Ladekarte', () => {
  test('Liste mit Zuhause oben, dann nach Euro, Kopfsatz nennt den teuren Ort', async ({ page }) => {
    await openPlaces(page);

    await expect(page.getByTestId('places-lead')).toHaveText('5 Orte in 12 Monaten, an einem zahlst du mehr als andere.');
    expect((await names(page)).map(n => n.trim())).toEqual(['Zuhause', 'IONITY', 'Tesla Supercharger Hamburg', 'Öffentlich', 'Ladeort']);
    await expect(page.locator('.places-map .places-pin')).toHaveCount(5);
    await expect(page.getByTestId('places-chip-high')).toHaveText(/andere zahlen 39 ct\/kWh, 15 Fahrer/);
  });

  test('Zeile antippen markiert den Punkt und zoomt hin, zweiter Tipp stellt den Ausschnitt wieder her', async ({ page }) => {
    // Die Fixture friert die Uhr ein, Leaflets flyTo rechnet mit Date.now und käme nie an.
    // Mit weniger Bewegung springt die Karte (setView), derselbe Weg wie für Nutzer mit dieser Einstellung.
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await openPlaces(page);
    await expect.poll(() => tileZoom(page)).not.toBeNull();
    const before = await tileZoom(page);

    const tesla = page.getByTestId('places-row-button').nth(2);
    await tesla.click();
    await expect(tesla).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.places-map .places-pin.is-active')).toHaveCount(1);
    await expect.poll(() => tileZoom(page), { timeout: 5000 }).toBe(15);

    await tesla.click();
    await expect(tesla).toHaveAttribute('aria-pressed', 'false');
    await expect(page.locator('.places-map .places-pin.is-active')).toHaveCount(0);
    await expect.poll(() => tileZoom(page), { timeout: 5000 }).toBe(before);
  });

  test('Zeitraum Monat ändert den Kopfsatz, unter 3 Orten keine Karte', async ({ page }) => {
    await openPlaces(page);
    await expect(page.getByTestId('places-map')).toBeVisible();

    await page.getByTestId('places-period-month').click();

    await expect(page.getByTestId('places-lead')).toHaveText('2 Orte im Oktober.');
    await expect(page.getByTestId('places-map')).toHaveCount(0);
    expect((await names(page)).map(n => n.trim())).toEqual(['Zuhause', 'IONITY']);
  });

  test('Fahrten-Schalter zählt Fahrten mit Linie, zeichnet sie und passt die Karte an', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await openPlaces(page);
    await expect.poll(() => tileZoom(page)).not.toBeNull();
    const before = await tileZoom(page);

    await expect(page.getByTestId('places-trips')).toHaveText(/2 Fahrten zeigen/);
    const toggle = page.getByRole('switch', { name: '2 Fahrten zeigen' });
    await expect(page.locator('.places-map path.places-trip')).toHaveCount(0);
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    await expect(page.locator('.places-map path.places-trip')).toHaveCount(2);
    // Gematchte Linie: der Router wird genannt
    await expect(page.locator('.places-map .leaflet-control-attribution')).toContainText('openrouteservice');
    // Die Fahrten liegen in Hamburg: die Karte rückt näher heran, beim Ausschalten zurück
    await expect.poll(() => tileZoom(page), { timeout: 5000 }).toBeGreaterThan(before!);
    await toggle.click();
    await expect.poll(() => tileZoom(page), { timeout: 5000 }).toBe(before);
  });

  test('Amber-Chip klappt den Preisvergleich in der Zeile auf', async ({ page }) => {
    await openPlaces(page);

    const chip = page.getByTestId('places-chip-high');
    await chip.click();
    await expect(chip).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('places-prices')).toContainText('Andere Fahrer zahlen dort im Schnitt 39,0 ct/kWh');
    await chip.click();
    await expect(chip).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('places-prices')).toBeHidden();
  });

  test('Betreiber eintragen öffnet die Ladung über der Karte, die Karte bleibt offen', async ({ page }) => {
    await openPlaces(page);

    await page.getByTestId('places-chip-operator').click();

    await expect(page.getByTestId('edit-log-modal')).toBeVisible();
    await expect(page.getByTestId('places-sheet')).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(2);
  });
});

import { test, expect, type Page } from '@playwright/test'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Bekannte Orte im Ortsschritt: steht der Nutzer in einer Zelle, in der er schon geladen hat, steht
 * "Hier hast du schon geladen" oben, ein Tap wählt und springt weiter. Die Kachelreihe mischt Säulen
 * und Orte ohne Namen (Ortsteil). Eine getippte Adresse ohne Säule und ohne bekannten Ort bekommt die
 * Chips Privat/Öffentlich. Reiner FE-Test, Backend und Nominatim sind gemockt.
 */
const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url')
const now = Math.floor(Date.now() / 1000)
const TOKEN = [b64url({ alg: 'none', typ: 'JWT' }),
  b64url({ sub: 'known@e2e.local', iat: now, exp: now + 3600, userId: '00000000-0000-0000-0000-00000000b003',
    username: 'e2e-known', demoAccount: false, authProvider: 'LOCAL', role: 'USER', premium: true }), 'sig'].join('.')
const CAR = { id: 'car-1', brand: 'Skoda', model: 'Enyaq', batteryCapacityKwh: 77, effectiveBatteryCapacityKwh: 77, status: 'ACTIVE' }
const HERE = { latitude: 52.5342, longitude: 13.4516 }
const SITE = { id: 'site-1', name: 'Aral Prenzlauer Berg', cpoName: 'Aral pulse', geohash: 'u33dc3n', maxAcKw: null, maxDcKw: 300, chargePoints: 4,
  fastCharging: true, address: 'Prenzlauer Allee 1, 10405 Berlin', plugTypes: ['CCS'], lastUsedAt: '2026-09-12T10:00:00', usageCount: 3 }
const known = (o: Record<string, unknown>) => ({ geohash: 'u33dc3', isPublic: false, usageCount: 4, lastUsedAt: '2026-09-27T18:00:00',
  cpoName: null, lastProviderId: null, placeName: 'Prenzlauer Berg', site: null, distanceMeters: null, here: false, ...o })
const STATION = { name: 'EnBW', known: true, distanceMeters: 180, maxAcKw: 22, maxDcKw: null, fastCharging: false, chargePoints: 2,
  geohash: 'u33dbx1', address: 'Sigridstraße 6, 10439 Berlin', plugTypes: [], registerId: null }

async function open(page: Page, places: (query: URLSearchParams) => unknown[], stations: unknown[] = []) {
  await page.addInitScript(({ t, keys }) => {
    localStorage.setItem('token', t)
    localStorage.setItem('onboarding-completed-known@e2e.local', 'true')
    localStorage.setItem('seen-announcements', JSON.stringify(keys))
    localStorage.setItem('ev_location_enabled', 'true')
  }, { t: TOKEN, keys: featureAnnouncements.map(a => a.key) })
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill(json([])))
  await page.route(url => url.pathname === '/api/cars', route => route.fulfill(json([CAR])))
  await page.route(url => url.pathname === '/api/charging-sites/known', route =>
    route.fulfill(json(places(new URL(route.request().url()).searchParams))))
  await page.route(url => url.pathname === '/api/charging-provider-tariffs/cpos/nearby-stations', route => route.fulfill(json(stations)))
}

test.describe('bekannte Orte mit Standort', () => {
  test.use({ geolocation: HERE, permissions: ['geolocation'] })

  test('Hier hast du schon geladen steht oben, ein Tap springt weiter', async ({ page }) => {
    await open(page, q => q.has('lat')
      ? [known({ here: true, distanceMeters: 120 }), known({ geohash: 'u33dc3n', isPublic: true, site: SITE, placeName: null, usageCount: 3, distanceMeters: 900 })]
      : [known({})], [STATION])
    await page.goto('/erfassen')
    const here = page.getByTestId('known-here')
    await expect(here).toContainText('Hier hast du schon geladen')
    await expect(here).toContainText('Prenzlauer Berg · 4 Ladungen, zuletzt 27. Sept.')
    // Die Zeile steht vor den Säulen
    const rows = page.locator('[data-testid="known-here"], button:has-text("EnBW")')
    await expect(rows.first()).toHaveAttribute('data-testid', 'known-here')
    await here.click()
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
  })

  test('Kachelreihe mischt Säule und Ort, Du bist hier am passenden Eintrag', async ({ page }) => {
    await open(page, () => [
      known({ geohash: 'u33dc3n', isPublic: true, site: SITE, placeName: null, usageCount: 7, distanceMeters: 900 }),
      known({ here: true, distanceMeters: 120 }),
    ], [STATION])
    await page.goto('/erfassen')
    const tiles = page.locator('[data-testid^="recent-site-"], [data-testid^="known-place-"]')
    await expect(tiles).toHaveCount(2)
    await expect(tiles.nth(0)).toContainText('Aral Prenzlauer Berg')
    await expect(tiles.nth(0)).toContainText('7 Ladungen')
    await expect(tiles.nth(1)).toContainText('Prenzlauer Berg')
    await expect(tiles.nth(1)).toContainText('Du bist hier')
    // Eine Säule aus der Liste wählen: die Zeile "hier" verschwindet ohne Nachfrage
    await page.getByRole('button', { name: /EnBW/ }).click()
    await expect(page.getByTestId('known-here')).toHaveCount(0)
  })
})

test('getippte Adresse ohne Säule und ohne bekannten Ort: Privat vorgewählt, Öffentlich ein Tap', async ({ page }) => {
  await open(page, () => [])
  await page.route(url => url.hostname === 'nominatim.openstreetmap.org', route => route.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify([{ place_id: 1, display_name: 'Dorfstraße 3, Oberdorf', lat: '49.17', lon: '10.33' }]) }))
  await page.goto('/erfassen')
  const search = page.locator('#wizard-place-search')
  await search.fill('Dorfstraße 3 Oberdorf')
  await search.press('Enter')
  await page.getByTestId('place-search-suggestion').click()
  const chips = page.getByTestId('known-chips')
  await expect(chips.getByRole('button', { name: 'Privat' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTestId('wizard-next')).toHaveAttribute('aria-disabled', 'false')
  await chips.getByRole('button', { name: 'Öffentlich' }).click()
  await expect(chips.getByRole('button', { name: 'Öffentlich' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTestId('known-here')).toHaveCount(0)
})

test('getippte Adresse in bekannter Zelle: der Ort ist vorgewählt, Weiter reicht', async ({ page }) => {
  await open(page, q => q.has('lat') ? [known({ here: true, distanceMeters: 60, isPublic: true, cpoName: 'Ionity', placeName: 'Oberdorf' })] : [])
  await page.route(url => url.hostname === 'nominatim.openstreetmap.org', route => route.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify([{ place_id: 1, display_name: 'Dorfstraße 3, Oberdorf', lat: '49.17', lon: '10.33' }]) }))
  await page.goto('/erfassen')
  const search = page.locator('#wizard-place-search')
  await search.fill('Dorfstraße 3 Oberdorf')
  await search.press('Enter')
  await page.getByTestId('place-search-suggestion').click()
  const here = page.getByTestId('known-here')
  await expect(here).toContainText('Oberdorf')
  await expect(here.locator('svg').last()).toBeVisible()
  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1')
  await page.getByTestId('wizard-next').click()
  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
})

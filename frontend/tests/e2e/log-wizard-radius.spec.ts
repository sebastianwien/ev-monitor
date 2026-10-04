import { test, expect, type Page } from '@playwright/test'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Ortswahl im Wizard: der Umkreis wächst von selbst (250 m, 1 km, 2,5 km). Bleibt er leer,
 * fragt die Seite "Wo hast du geladen?" und zeigt Suche, "Hier privat geladen" und "Öffentlich geladen".
 * Eine gewählte Adresse verhält sich wie die eigene Position. Reiner FE-Test, Backend und Nominatim sind gemockt.
 */
const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url')
const now = Math.floor(Date.now() / 1000)
const TOKEN = [b64url({ alg: 'none', typ: 'JWT' }),
  b64url({ sub: 'radius@e2e.local', iat: now, exp: now + 3600, userId: '00000000-0000-0000-0000-00000000b002',
    username: 'e2e-radius', demoAccount: false, authProvider: 'LOCAL', role: 'USER', premium: true }), 'sig'].join('.')
const CAR = { id: 'car-1', brand: 'Skoda', model: 'Enyaq', batteryCapacityKwh: 77, effectiveBatteryCapacityKwh: 77, status: 'ACTIVE' }
const HERE = { latitude: 52.5342, longitude: 13.4516 }
/** Collapse klappt animiert zu (Fallback 400 ms): erst danach sagt "sichtbar" etwas aus */
const settle = (page: Page) => page.waitForTimeout(600)
const STATION = { name: 'EnBW', known: true, distanceMeters: 480, maxAcKw: 22, maxDcKw: null, fastCharging: false, chargePoints: 2,
  geohash: 'u33dbx1', address: 'Sigridstraße 6, 10439 Berlin', plugTypes: [], registerId: null }

async function open(page: Page, byRadius: Record<number, unknown[]>, status = 200) {
  await page.addInitScript(({ t, keys }) => {
    localStorage.setItem('token', t)
    localStorage.setItem('onboarding-completed-radius@e2e.local', 'true')
    localStorage.setItem('seen-announcements', JSON.stringify(keys))
    localStorage.setItem('ev_location_enabled', 'true')
  }, { t: TOKEN, keys: featureAnnouncements.map(a => a.key) })
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill(json([])))
  await page.route(url => url.pathname === '/api/cars', route => route.fulfill(json([CAR])))
  const radii: number[] = []
  await page.route(url => url.pathname === '/api/charging-provider-tariffs/cpos/nearby-stations', route => {
    const r = Number(new URL(route.request().url()).searchParams.get('radius') ?? 250)
    radii.push(r)
    return status === 200 ? route.fulfill(json(byRadius[r] ?? [])) : route.fulfill({ status, body: '' })
  })
  return radii
}

test.describe('wachsender Umkreis', () => {
  test.use({ geolocation: HERE, permissions: ['geolocation'] })

  test('leer bei 250 m, Treffer bei 1 km', async ({ page }) => {
    const radii = await open(page, { 1000: [STATION] })
    await page.goto('/erfassen')
    await expect(page.locator('p.uppercase', { hasText: 'Im Umkreis von 1 km' })).toBeVisible()
    await expect(page.getByRole('button', { name: /EnBW/ })).toBeVisible()
    await expect(page.getByTestId('expand-radius')).toHaveText(/Umkreis auf 2,5 km erweitern/)
    expect(radii).toEqual([250, 1000])
  })

  test('bis 2,5 km leer: Frage, offene Suche, privat hier oder öffentlich', async ({ page }) => {
    const radii = await open(page, {})
    await page.goto('/erfassen')
    const empty = page.getByTestId('wizard-no-stations')
    await expect(empty).toContainText('Im Umkreis von 2,5 km ist keine Ladesäule bekannt')
    await expect(empty).toContainText('Such den Ort oder wähl darunter, wie du geladen hast')
    expect(radii).toEqual([250, 1000, 2500])
    // Die Suche steht offen da: ein Tap ins Feld, kein "Woanders geladen" davor
    await settle(page)
    await expect(page.locator('#wizard-place-search')).toBeVisible()
    await expect(page.getByTestId('place-here')).toContainText('Hier privat geladen')
    await expect(page.getByTestId('place-here')).toContainText('an deinem Standort')
    await expect(page.getByTestId('place-other')).toContainText('Öffentlich geladen')
  })

  test('Fehler (Drossel): keine Umkreis-Behauptung, aber die Suche steht offen', async ({ page }) => {
    const radii = await open(page, {}, 429)
    await page.goto('/erfassen')
    await expect(page.getByTestId('place-here')).toBeVisible()
    await settle(page)
    await expect(page.locator('#wizard-place-search')).toBeVisible()
    await expect(page.getByTestId('wizard-no-stations')).toHaveCount(0)
    expect(radii).toEqual([250])
  })

  test('Such-Taste startet die Adresssuche ohne Extra-Tap', async ({ page }) => {
    await open(page, {})
    let nominatim = ''
    await page.route(url => url.hostname === 'nominatim.openstreetmap.org', route => {
      nominatim = route.request().url()
      return route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify([{ place_id: 1, display_name: 'Sigridstraße 6, Berlin', lat: '52.5359', lon: '13.4579' }]) })
    })
    await page.goto('/erfassen')
    await expect(page.getByTestId('wizard-no-stations')).toBeVisible()
    const search = page.locator('#wizard-place-search')
    await search.fill('Sigridstraße 6')
    await search.press('Enter')
    await expect(page.getByTestId('place-search-suggestion')).toContainText('Sigridstraße 6')
    expect(nominatim).toContain('Sigridstra')
  })
})

test.describe('Ort per Adresse', () => {
  test.use({ permissions: [] })

  test('Säulen an der Adresse, darüber "Hier privat geladen" mit der Adresse; Tap wählt privat', async ({ page }) => {
    await open(page, { 250: [STATION] })
    await page.route(url => url.hostname === 'nominatim.openstreetmap.org', route =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify([{ place_id: 1, display_name: 'Sigridstraße 6, Berlin', lat: '52.5359', lon: '13.4579' }]) }))
    await page.goto('/erfassen')
    const search = page.locator('#wizard-place-search')
    await search.fill('Sigridstraße 6')
    await search.press('Enter')
    await page.getByTestId('place-search-suggestion').click()

    await expect(page.getByRole('button', { name: /EnBW/ })).toBeVisible()
    await expect(page.getByTestId('place-other')).toContainText('Säule nicht dabei?')
    const here = page.getByTestId('place-here')
    await expect(here).toContainText('Hier privat geladen')
    await expect(here).toContainText('Sigridstraße 6, Berlin')
    await here.click()
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
  })
})

test.describe('ungenaue Ortung', () => {
  test.use({ geolocation: { ...HERE, accuracy: 1200 }, permissions: ['geolocation'] })

  test('sagt es offen und bietet erneutes Orten an', async ({ page }) => {
    await open(page, {})
    await page.goto('/erfassen')
    const empty = page.getByTestId('wizard-no-stations')
    await expect(empty).toContainText('Dein Standort ist ungenau (±1,2 km)')
    await expect(page.getByTestId('wizard-relocate')).toBeVisible()
  })
})

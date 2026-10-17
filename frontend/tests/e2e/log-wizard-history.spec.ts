import { test, expect, type Page } from '@playwright/test'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Ladeart, Karte und Preis kommen aus der eigenen Historie am Ort. Gemischte Standorte (AC und DC)
 * raten kein DC aus dem Register: der Preisvorschlag wird ohne Ladeart abgefragt und bringt die
 * Ladeart der letzten eigenen Ladung dort mit. Reiner FE-Test, das Backend ist gemockt.
 */
const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url')
const now = Math.floor(Date.now() / 1000)
const TOKEN = [b64url({ alg: 'none', typ: 'JWT' }),
  b64url({ sub: 'history@e2e.local', iat: now, exp: now + 3600, userId: '00000000-0000-0000-0000-00000000b003',
    username: 'e2e-history', demoAccount: false, authProvider: 'LOCAL', role: 'USER', premium: true }), 'sig'].join('.')
const CAR = { id: 'car-1', brand: 'Tesla', model: 'Model 3', batteryCapacityKwh: 75, effectiveBatteryCapacityKwh: 75, status: 'ACTIVE' }
const HERE = { latitude: 52.5345, longitude: 13.4562 }
const CARD = { id: 'card-1', providerName: 'Kaufland', label: 'Kaufland', acPricePerKwh: 0.29, dcPricePerKwh: 0.44,
  monthlyFeeEur: 0, sessionFeeEur: 0, activeFrom: '2026-01-01', activeUntil: null, isPrivate: false }
const base = { known: true, distanceMeters: 30, chargePoints: 2, address: 'Storkower Str. 139, 10407 Berlin', plugTypes: [], registerId: 1 }
const MIXED = { ...base, name: 'Kaufland', maxAcKw: 43, maxDcKw: 50, fastCharging: true, geohash: 'u33df5w' }
const DC_ONLY = { ...base, name: 'IONITY', maxAcKw: null, maxDcKw: 350, fastCharging: true, geohash: 'u33df5x' }

const settle = (page: Page) => page.waitForTimeout(600)

/** Öffnet den Wizard; liefert die Preisvorschlag-Anfragen, die der Client gestellt hat. */
async function open(page: Page, station: unknown, suggestion: { status: number; body?: unknown }) {
  await page.addInitScript(({ t, keys }) => {
    localStorage.setItem('token', t)
    localStorage.setItem('onboarding-completed-history@e2e.local', 'true')
    localStorage.setItem('seen-announcements', JSON.stringify(keys))
    localStorage.setItem('ev_location_enabled', 'true')
  }, { t: TOKEN, keys: featureAnnouncements.map(a => a.key) })
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill(json([])))
  await page.route(url => url.pathname === '/api/cars', route => route.fulfill(json([CAR])))
  await page.route(url => url.pathname === '/api/users/me/charging-providers', route => route.fulfill(json([CARD])))
  await page.route(url => url.pathname === '/api/charging-provider-tariffs/cpos/nearby-stations', route => route.fulfill(json([station])))
  const asked: URLSearchParams[] = []
  await page.route(url => url.pathname === '/api/logs/price-suggestion', route => {
    asked.push(new URL(route.request().url()).searchParams)
    return suggestion.status === 200 ? route.fulfill(json(suggestion.body)) : route.fulfill({ status: suggestion.status, body: '' })
  })
  return asked
}

/** Station tippen, bis Schritt 2 (Zahlen) offen ist */
async function pickStationAndContinue(page: Page, name: RegExp) {
  await page.goto('/erfassen')
  await page.getByRole('button', { name }).click()
  await settle(page)
  await page.getByTestId('wizard-next').click()
  await expect(page.getByTestId('charging-type-ac')).toBeVisible()
}

test.describe('Ladeart, Karte und Preis aus der Historie', () => {
  test.use({ geolocation: HERE, permissions: ['geolocation'] })

  test('gemischter Standort, zuletzt AC: AC, Karte und Preis vom letzten Mal, Abfrage ohne Ladeart', async ({ page }) => {
    const asked = await open(page, MIXED, { status: 200, body: { costPerKwh: 0.29, chargingProviderId: 'card-1', chargingType: 'AC' } })
    await page.goto('/erfassen')
    await page.getByRole('button', { name: /Kaufland/ }).click()
    await expect(page.getByTestId('card-card-1').first()).toHaveAttribute('aria-pressed', 'true')
    expect(asked.length).toBeGreaterThan(0)
    expect(asked.every(p => !p.has('chargingType'))).toBe(true)
    await settle(page)
    await page.getByTestId('wizard-next').click()
    await expect(page.getByTestId('charging-type-ac')).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('cost-derived')).toBeVisible()
  })

  test('gemischter Standort, zuletzt DC: die Historie setzt DC mit dem DC-Preis der Karte', async ({ page }) => {
    await open(page, MIXED, { status: 200, body: { costPerKwh: 0.44, chargingProviderId: 'card-1', chargingType: 'DC' } })
    await pickStationAndContinue(page, /Kaufland/)
    await expect(page.getByTestId('charging-type-dc')).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('charging-type-ac')).toHaveAttribute('aria-checked', 'false')
  })

  test('gemischter Standort ohne Historie: kein geratenes DC, AC bleibt', async ({ page }) => {
    await open(page, MIXED, { status: 204 })
    await pickStationAndContinue(page, /Kaufland/)
    await expect(page.getByTestId('charging-type-ac')).toHaveAttribute('aria-checked', 'true')
  })

  test('nur die Karte (nie bezahlt in dieser Ladeart): Karte ist gewählt, AC bleibt', async ({ page }) => {
    await open(page, MIXED, { status: 200, body: { chargingProviderId: 'card-1' } })
    await page.goto('/erfassen')
    await page.getByRole('button', { name: /Kaufland/ }).click()
    await expect(page.getByTestId('card-card-1').first()).toHaveAttribute('aria-pressed', 'true')
  })

  test('reiner DC-Standort: DC aus dem Register, Abfrage mit Ladeart DC', async ({ page }) => {
    const asked = await open(page, DC_ONLY, { status: 204 })
    await pickStationAndContinue(page, /IONITY/)
    await expect(page.getByTestId('charging-type-dc')).toHaveAttribute('aria-checked', 'true')
    expect(asked.some(p => p.get('chargingType') === 'DC')).toBe(true)
  })
})

import { test, expect, type Page, type Route } from '@playwright/test'
import geohash from 'ngeohash'
import { TEST_USER } from './global-setup'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Säulenkarte auf dem Handy, API, Kacheln und Ortssuche gemockt: Reiter öffnen, Pins mit Preis,
 * Säule mit allen Tarifen und Gebühren, zurück, Ladeart wechseln, Ort suchen, Fehler und erneut.
 */
const KROEPCKE = { latitude: 52.3745, longitude: 9.7385 }
test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, geolocation: KROEPCKE, permissions: ['geolocation'] })

const EWE = {
  id: '1', name: 'EWE Go', known: true, latitude: 52.3747, longitude: 9.7383, maxAcKw: 22, maxDcKw: 150, chargePoints: 4,
  plugTypes: ['Typ 2', 'CCS'], address: 'Musterstraße 12, 30159 Hannover', openingHours: '247', payment: 'Ladekarte, App',
  priceAc: { pricePerKwh: 0.52, cardName: 'EWE Go', variant: null }, priceDc: { pricePerKwh: 0.52, cardName: 'EWE Go', variant: null },
}
const STADTWERK = {
  // rund 280 m entfernt: weit genug, dass die Karte die beiden Pins nicht zu einer Gruppe fasst
  id: '2', name: 'Stadtwerke Musterstadt', known: false, latitude: 52.3770, longitude: 9.7385, maxAcKw: 22, maxDcKw: null,
  chargePoints: 2, plugTypes: ['Typ 2'], address: 'Musterweg 3, 30159 Hannover', openingHours: null, payment: null,
  priceAc: null, priceDc: null,
}
const STATIONS = [EWE, STADTWERK]
const TARIFFS = [
  { name: 'EWE Go', variant: null, pricePerKwh: 0.52, monthlyFeeEur: 0, verifiedOn: '2026-11-01', direct: true,
    sessionFeeEur: 0, blockingFeePerMin: null, blockingFeeAfterMin: null, blockingFeeCapEur: null },
  { name: 'ADAC e-Charge', variant: null, pricePerKwh: 0.75, monthlyFeeEur: 0, verifiedOn: '2026-11-01', direct: false,
    sessionFeeEur: 0, blockingFeePerMin: 0.15, blockingFeeAfterMin: 45, blockingFeeCapEur: null },
]
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64')

async function mockApis(page: Page, opts: { failFirst?: boolean } = {}) {
  let failing = !!opts.failFirst
  await page.route(url => url.pathname === '/api/charging-stations', (route: Route) => {
    const cell = new URL(route.request().url()).searchParams.get('cells')!
    if (failing) return route.fulfill({ json: { cells: [], missing: [cell] } })
    const stations = STATIONS.filter(s => geohash.encode(s.latitude, s.longitude, 6) === cell)
    return route.fulfill({ json: { cells: [{ cell, inGermany: true, stations }], missing: [] } })
  })
  await page.route(url => url.pathname === '/api/charging-stations/tariffs',
    route => route.fulfill({ json: { cpoName: 'EWE Go', chargingType: 'DC', tariffs: TARIFFS } }))
  await page.route(/tile\.openstreetmap\.org/, route => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }))
  await page.route(/photon\.komoot\.io/, route => route.fulfill({ json: { features: [
    { geometry: { coordinates: [9.7385, 52.3745] }, properties: { name: 'Kröpcke', city: 'Hannover', postcode: '30159', type: 'house', osm_id: 1, osm_type: 'N' } },
  ] } }))
  return { recover: () => { failing = false } }
}

async function login(page: Page) {
  await page.addInitScript((k: string[]) => localStorage.setItem('seen-announcements', JSON.stringify(k)), featureAnnouncements.map(a => a.key))
  await page.goto('/login')
  await page.fill('input[type="text"]', TEST_USER.email)
  await page.fill('input[type="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/dashboard|logs|cars|story/)
}

const openStations = async (page: Page) => {
  await page.locator('nav.bottom-nav').getByRole('link', { name: 'Säulen' }).click()
  await page.waitForURL(/\/stations$/)
}
/** Pins sind Knöpfe mit "<Betreiber>, <Preis> je kWh" */
const pin = (page: Page, name: RegExp) => page.getByTestId('stations-map').getByRole('button', { name })

test('Reiter Säulen: Pin mit Preis, Säule mit Tarifen und Gebühren, zurück, Ladeart wechseln', async ({ page }) => {
  await mockApis(page)
  await login(page)
  await openStations(page)

  const ewePin = pin(page, /EWE Go, 0,52 € je kWh/)
  await expect(ewePin).toBeVisible()
  await expect(page.getByTestId('stations-count')).toContainText('1 Säule hier')

  await ewePin.click()
  const detail = page.getByTestId('station-detail')
  await expect(detail).toBeVisible()
  await expect(detail).toContainText('Musterstraße 12, 30159 Hannover')
  await expect(detail).toContainText('So viel kostet Laden hier')
  await expect(detail).toContainText('Säule des Anbieters')
  await expect(detail).toContainText('Keine Start- und Blockiergebühr')
  await expect(detail).toContainText('Blockiergebühr ab 45 Min.: 0,15 € je Min.')
  await expect(detail.getByTestId('station-route')).toHaveAttribute('href', /destination=52\.3747,9\.7383/)

  await page.getByTestId('station-back').click()
  await expect(detail).toBeHidden()
  await expect(page.getByTestId('station-row')).toHaveCount(1)

  await page.getByTestId('stations-type-ac').click()
  await expect(page.getByTestId('station-row')).toHaveCount(2)
  await expect(page.getByTestId('station-row').filter({ hasText: 'Stadtwerke Musterstadt' })).toBeVisible()
  await expect(pin(page, /^Stadtwerke Musterstadt$/)).toBeVisible()
})

test('Ort suchen: Vorschlag antippen bringt die Karte dorthin', async ({ page }) => {
  await mockApis(page)
  await login(page)
  await openStations(page)

  await page.getByTestId('stations-search').fill('Kröp')
  const suggestion = page.getByTestId('stations-suggestions').getByRole('button', { name: /Kröpcke/ })
  await expect(suggestion).toBeVisible()
  await suggestion.click()
  await expect(page.getByTestId('stations-suggestions')).toBeHidden()
  await expect(page.getByTestId('station-row').first()).toBeVisible()
})

test('Register antwortet nicht: Hinweis mit Erneut versuchen, danach die Säulen', async ({ page }) => {
  const api = await mockApis(page, { failFirst: true })
  await login(page)
  await openStations(page)

  await expect(page.getByTestId('stations-notice')).toContainText('Säulen konnten nicht geladen werden.')
  api.recover()
  await page.getByTestId('stations-retry').click()
  await expect(page.getByTestId('stations-notice')).toBeHidden()
  await expect(page.getByTestId('station-row')).toHaveCount(1)
})

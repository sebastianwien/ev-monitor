import { test, expect } from '@playwright/test'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Sprachlog im Wizard: Aufnahme -> Prüfen -> Speichern. Reiner FE-Test: Chromium liefert über
 * --use-fake-device-for-media-stream ein Testsignal als Mikrofon, das Backend ist gemockt. Admin-Zugang
 * per unsigniertem JWT (das FE dekodiert nur; Rechte prüft VoiceLogControllerIntegrationTest).
 * iOS-Safari (MP4-Aufnahme) bleibt Handarbeit am Gerät.
 */
test.skip(({ browserName }) => browserName !== 'chromium', 'Fake-Mikrofon gibt es nur in Chromium')
test.use({ permissions: ['microphone'], launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] } })

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url')
const now = Math.floor(Date.now() / 1000)
const token = (role: 'ADMIN' | 'USER') => [
  b64url({ alg: 'none', typ: 'JWT' }),
  b64url({ sub: 'voice@e2e.local', iat: now, exp: now + 3600, userId: '00000000-0000-0000-0000-00000000b001',
    username: 'e2e-voice', demoAccount: false, authProvider: 'LOCAL', role, premium: true }),
  'sig',
].join('.')

const CAR = { id: 'car-1', brand: 'Skoda', model: 'Enyaq', batteryCapacityKwh: 77, effectiveBatteryCapacityKwh: 77, status: 'ACTIVE' }
const DRAFT = {
  transcript: 'Zuhause geladen, 32 Kilowattstunden, 9 Euro 60, Akku auf 80 Prozent, Tacho 48210',
  fields: { kwhCharged: 32, kwhAtVehicle: null, socBefore: null, socAfter: 80, odometerKm: 48210, costEur: 9.6, pricePerKwh: null,
    loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: 'AC', routeType: null, tireType: null,
    placeIndex: 0, placeKind: 'home', spokenOperator: null, tariffIndex: null, uncertain: ['costEur'] },
  place: { kind: 'home', station: null, site: null, cpoName: null },
  chargingProviderId: null,
  usage: { limit: null, remaining: null, resetsOn: '2026-11-01' },
}

async function open(page: import('@playwright/test').Page, role: 'ADMIN' | 'USER') {
  await page.addInitScript(({ t, keys }) => {
    localStorage.setItem('token', t)
    localStorage.setItem('onboarding-completed-voice@e2e.local', 'true')
    localStorage.setItem('seen-announcements', JSON.stringify(keys))
  }, { t: token(role), keys: featureAnnouncements.map(a => a.key) })
  await page.route(url => url.pathname.startsWith('/api/'), route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route(url => url.pathname === '/api/cars', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([CAR]) }))
}

test('Admin spricht den Ladevorgang ein und speichert ihn von der Prüfseite', async ({ page }) => {
  await open(page, 'ADMIN')
  let draftRequest: { contentType: string; body: string } | null = null
  await page.route(url => url.pathname === '/api/logs/voice-draft', route => {
    draftRequest = { contentType: route.request().headers()['content-type'] ?? '', body: route.request().postDataBuffer()?.toString('latin1') ?? '' }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DRAFT) })
  })
  let saved: Record<string, unknown> | null = null
  await page.route(url => url.pathname === '/api/logs', route => {
    if (route.request().method() !== 'POST') return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    saved = route.request().postDataJSON()
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ coinsAwarded: 5 }) })
  })
  await page.goto('/erfassen')

  const mic = page.getByTestId('voice-mic')
  await mic.click()
  // Erstes Antippen: erst der Transparenz-Satz, dann nimmt "Verstanden" auf
  await expect(page.getByTestId('voice-consent')).toContainText('Mistral AI')
  await page.getByTestId('voice-consent-ok').click()
  await expect(page.getByTestId('voice-status')).toContainText('Ich höre zu')
  await page.waitForTimeout(1200)
  await mic.click()

  await expect(page.getByTestId('voice-transcript')).toContainText('32 Kilowattstunden')
  expect(draftRequest!.contentType).toContain('multipart/form-data')
  expect(draftRequest!.body).toContain('name="carId"')
  expect(draftRequest!.body).toContain('name="timeZone"')
  expect(draftRequest!.body).toMatch(/name="audio"; filename="voice\.(webm|m4a|ogg)"/)
  await expect(page.getByTestId('voice-flags')).toContainText('Kosten')
  await expect(page.getByTestId('summary-cost')).toHaveAttribute('data-flagged', 'true')
  await expect(page.getByTestId('summary-energy')).toContainText('32')
  await expect(page.getByTestId('voice-usage')).toHaveCount(0)

  await page.getByTestId('wizard-next').click()
  await expect.poll(() => saved).not.toBeNull()
  expect(saved).toMatchObject({ carId: 'car-1', kwhCharged: 32, costEur: 9.6, odometerKm: 48210, socAfterChargePercent: 80,
    isPublicCharging: false, chargingType: 'AC' })
  expect(saved).not.toHaveProperty('voiceUsed')
})

test('Server versteht nichts: Hinweis am Mikrofon, Wizard bleibt in Schritt 1', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen', 'true'))
  await page.route(url => url.pathname === '/api/logs/voice-draft', route =>
    route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ code: 'VOICE_NOT_UNDERSTOOD' }) }))
  await page.goto('/erfassen')

  const mic = page.getByTestId('voice-mic')
  await mic.click()
  await expect(page.getByTestId('voice-status')).toContainText('Ich höre zu')
  await page.waitForTimeout(800)
  await mic.click()
  await expect(page.getByTestId('voice-status')).toContainText('Nichts verstanden')
  await expect(page.getByTestId('place-here')).toBeVisible()
})

test('Nicht-Admins sehen kein Mikrofon', async ({ page }) => {
  await open(page, 'USER')
  await page.goto('/erfassen')
  await expect(page.getByTestId('place-here')).toBeVisible()
  await expect(page.getByTestId('voice-mic')).toHaveCount(0)
})

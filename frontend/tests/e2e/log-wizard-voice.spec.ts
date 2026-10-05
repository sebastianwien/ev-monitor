import { test, expect } from '@playwright/test'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Sprachlog im Wizard: Aufnahme -> Prüfen -> Speichern. Reiner FE-Test: Chromium liefert über
 * --use-fake-device-for-media-stream ein Testsignal als Mikrofon, das Backend ist gemockt. Admin-Zugang
 * per unsigniertem JWT (das FE dekodiert nur; Rechte prüft VoiceLogControllerIntegrationTest).
 * iOS-Safari (MP4-Aufnahme) bleibt Handarbeit am Gerät.
 */
test.skip(({ browserName }) => browserName !== 'chromium', 'Fake-Mikrofon gibt es nur in Chromium')
// Mit Position: "Hier privat" gibt es nur mit Ort
test.use({ geolocation: { latitude: 52.5342, longitude: 13.4516 }, permissions: ['microphone', 'geolocation'], launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] } })

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
    placeIndex: 0, placeKind: 'home', spokenOperator: null, spokenAddress: null, tariffIndex: null, uncertain: ['costEur'] },
  place: { kind: 'home', station: null, site: null, cpoName: null },
  chargingProviderId: null,
  usage: { plan: 'admin', limit: null, remaining: null, resetsOn: '2026-11-01' },
}
const ADMIN_QUOTA = { plan: 'admin', limit: null, remaining: null, exhausted: false, resetsOn: '2026-11-01' }

type Page = import('@playwright/test').Page
/** Plausible-Events der Seite: [name, props] */
const events = (page: Page) => page.evaluate(() => (window as any).__events as [string, Record<string, unknown>][])
const voiceSteps = async (page: Page) => (await events(page)).filter(([e]) => e === 'Voice').map(([, p]) => p.step)

/** Aufnahme über das Vollbild: Mikrofon, kurz sprechen (Fake-Signal), "Fertig". */
async function record(page: Page, mic = page.getByTestId('voice-mic').first()) {
  await mic.click()
  await expect(page.getByTestId('voice-sheet')).toBeVisible()
  await expect(page.getByTestId('voice-status')).toContainText('Ich höre zu')
  await page.waitForTimeout(900)
  await page.getByTestId('voice-done').click()
}

/** quota: Stand von GET /logs/voice-quota; null = 404 wie im Testbetrieb für Nicht-Admins */
async function open(page: Page, role: 'ADMIN' | 'USER', quota: object | null = role === 'ADMIN' ? ADMIN_QUOTA : null) {
  // Echtes Plausible-Skript blockieren, sonst ersetzt es den Mitschreiber unten
  await page.route(url => url.hostname === 'plausible.io', route => route.abort())
  await page.addInitScript(() => {
    ;(window as any).__events = []
    ;(window as any).plausible = (e: string, o?: { props?: Record<string, unknown> }) => (window as any).__events.push([e, o?.props ?? {}])
  })
  await page.addInitScript(({ t, keys }) => {
    localStorage.setItem('token', t)
    localStorage.setItem('onboarding-completed-voice@e2e.local', 'true')
    localStorage.setItem('seen-announcements', JSON.stringify(keys))
  }, { t: token(role), keys: featureAnnouncements.map(a => a.key) })
  await page.route(url => url.pathname.startsWith('/api/'), route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route(url => url.pathname === '/api/cars', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([CAR]) }))
  await page.route(url => url.pathname === '/api/logs/voice-quota', route => quota
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(quota) })
    : route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }))
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
  // Erstes Antippen: erst der Transparenz-Satz (inkl. Adresssuche), dann nimmt "Verstanden" auf
  await expect(page.getByTestId('voice-consent')).toContainText('Mistral AI')
  await expect(page.getByTestId('voice-consent')).toContainText('OpenStreetMap')
  await page.getByTestId('voice-consent-ok').click()
  // Vollbild mit Spickzettel, Fertig im Daumenbereich
  await expect(page.getByTestId('voice-sheet')).toBeVisible()
  await expect(page.getByTestId('voice-status')).toContainText('Ich höre zu')
  await expect(page.getByTestId('voice-chips-core')).toContainText('Tachostand')
  await expect(page.getByTestId('voice-chips-more')).toContainText('Ladekarte')
  await page.waitForTimeout(1200)
  await page.getByTestId('voice-done').click()

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
  await expect.poll(() => voiceSteps(page)).toEqual(['consent', 'open', 'stop', 'draft', 'saved'])
  const draftEvent = (await events(page)).find(([e, p]) => e === 'Voice' && p.step === 'draft')![1]
  expect(draftEvent).toMatchObject({ entry: 'create', filled: 6, uncertain: 1, place: 'match' })
  expect(JSON.stringify(await events(page))).not.toContain('48210')
})

test('Server versteht nichts: Hinweis am Mikrofon, Wizard bleibt in Schritt 1', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  await page.route(url => url.pathname === '/api/logs/voice-draft', route =>
    route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ code: 'VOICE_NOT_UNDERSTOOD' }) }))
  await page.goto('/erfassen')

  await record(page)
  await expect(page.getByTestId('voice-sheet')).toHaveCount(0)
  await expect(page.getByTestId('voice-problem')).toContainText('Nichts verstanden')
  await expect(page.getByTestId('place-here')).toBeVisible()
  await expect.poll(() => events(page)).toContainEqual(['Voice', { step: 'error', entry: 'create', kind: 'not_understood' }])
})

test('Ohne Freigabe (404 beim Kontingent) kein Mikrofon', async ({ page }) => {
  await open(page, 'USER')
  await page.goto('/erfassen')
  await expect(page.getByTestId('place-here')).toBeVisible()
  await expect(page.getByTestId('voice-mic')).toHaveCount(0)
})

test('Abbrechen im Vollbild verwirft die Aufnahme ohne Upload', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  let uploads = 0
  await page.route(url => url.pathname === '/api/logs/voice-draft', route => { uploads++; return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DRAFT) }) })
  await page.goto('/erfassen')
  await page.getByTestId('voice-mic').click()
  await expect(page.getByTestId('voice-sheet')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('voice-sheet')).toHaveCount(0)
  await page.waitForTimeout(300)
  expect(uploads).toBe(0)
  expect(await voiceSteps(page)).toEqual(['open', 'cancel'])
})

test('Fehlendes nachsprechen: zweite Aufnahme füllt die Lücke, der Rest bleibt', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  const first = { ...DRAFT, fields: { ...DRAFT.fields, odometerKm: null, uncertain: [] } }
  const second = { ...DRAFT, transcript: 'Tacho 48210', place: null,
    fields: { ...DRAFT.fields, kwhCharged: null, socAfter: null, costEur: null, chargingType: null, placeIndex: null, placeKind: null, odometerKm: 48210, uncertain: [] } }
  const drafts = [first, second]
  await page.route(url => url.pathname === '/api/logs/voice-draft', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(drafts.shift()) }))
  let saved: Record<string, unknown> | null = null
  await page.route(url => url.pathname === '/api/logs', route => {
    if (route.request().method() !== 'POST') return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    saved = route.request().postDataJSON()
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ coinsAwarded: 5 }) })
  })
  await page.goto('/erfassen')

  await record(page)
  // Tacho fehlt: Schritt 2 bietet das Nachsprechen an
  const missing = page.locator('[data-testid="voice-capture"][data-variant="inline"]')
  await expect(missing).toContainText('Fehlt noch: Tachostand')
  await record(page, missing.getByTestId('voice-mic'))

  await expect(page.getByTestId('voice-transcript')).toContainText('Tacho 48210')
  await expect(page.getByTestId('summary-energy')).toContainText('32')
  await page.getByTestId('wizard-next').click()
  await expect.poll(() => saved).not.toBeNull()
  expect(saved).toMatchObject({ kwhCharged: 32, odometerKm: 48210, socAfterChargePercent: 80, costEur: 9.6 })
  await expect.poll(() => voiceSteps(page)).toEqual(['open', 'stop', 'draft', 'open', 'retry', 'stop', 'draft', 'saved'])
})

test('Gesprochene Adresse ohne Säule dort: privat an dieser Adresse', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  const atAddress = { ...DRAFT, transcript: 'Bei meinen Eltern, Lindenweg 4 in Bamberg, 18 kWh, kostenlos, 80 Prozent, Tacho 41020',
    place: { kind: 'other', station: null, site: null, cpoName: null },
    fields: { ...DRAFT.fields, kwhCharged: 18, costEur: 0, odometerKm: 41020, chargingType: null, placeIndex: null, placeKind: 'other',
      spokenAddress: 'Lindenweg 4, Bamberg', uncertain: [] } }
  await page.route(url => url.pathname === '/api/logs/voice-draft', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(atAddress) }))
  let nominatimQuery = ''
  await page.route(url => url.hostname === 'nominatim.openstreetmap.org', route => {
    nominatimQuery = new URL(route.request().url()).searchParams.get('q') ?? ''
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
      { place_id: 1, lat: '49.8988', lon: '10.9028', display_name: 'Lindenweg 4, Bamberg', address: { road: 'Lindenweg', house_number: '4', city: 'Bamberg' } }]) })
  })
  let saved: Record<string, unknown> | null = null
  await page.route(url => url.pathname === '/api/logs', route => {
    if (route.request().method() !== 'POST') return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    saved = route.request().postDataJSON()
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ coinsAwarded: 5 }) })
  })
  await page.goto('/erfassen')
  await record(page)

  // Alles gesagt, Adresse gefunden, keine Säule dort: direkt auf die Prüfseite
  await expect(page.getByTestId('voice-transcript')).toContainText('Lindenweg')
  expect(nominatimQuery).toBe('Lindenweg 4, Bamberg')
  await page.getByTestId('wizard-next').click()
  await expect.poll(() => saved).not.toBeNull()
  expect(saved).toMatchObject({ isPublicCharging: false, latitude: 49.8988, longitude: 10.9028, kwhCharged: 18, costEur: 0 })
  const draftEvent = (await events(page)).find(([e, p]) => e === 'Voice' && p.step === 'draft')![1]
  expect(draftEvent.place).toBe('address')
})

test('Free mit 2 übrigen Aufnahmen: Hinweis mit Supporter-Link, Klick wird gemessen', async ({ page }) => {
  await open(page, 'USER', { plan: 'free', limit: 5, remaining: 2, exhausted: false, resetsOn: '2026-11-01' })
  await page.goto('/erfassen')

  await expect(page.getByTestId('voice-quota-low')).toContainText('Noch 2 Aufnahmen im')
  await expect.poll(() => events(page)).toContainEqual(['Voice', { step: 'quota', kind: 'low', plan: 'free' }])
  await page.getByTestId('voice-upsell').click()
  await expect(page).toHaveURL(/\/supporter/)
  await expect.poll(() => events(page)).toContainEqual(['Voice', { step: 'upsell', entry: 'create', kind: 'low' }])
  await expect.poll(() => events(page)).toContainEqual(['upsell_viewed', { page: 'supporter', source: 'voice' }])
})

test('Supporter sieht den Zähler erst bei den letzten fünf, ohne Upgrade', async ({ page }) => {
  await open(page, 'USER', { plan: 'paid', limit: 30, remaining: 5, exhausted: false, resetsOn: '2026-11-01' })
  await page.goto('/erfassen')

  await expect(page.getByTestId('voice-quota-low')).toContainText('Noch 5 von 30 Aufnahmen im')
  await expect(page.getByTestId('voice-upsell')).toHaveCount(0)
})

test('Deckel beim Aufnehmen erreicht: Mikrofon aus, Supporter-Angebot, Tippen geht weiter', async ({ page }) => {
  await open(page, 'USER', { plan: 'free', limit: 5, remaining: 1, exhausted: false, resetsOn: '2026-11-01' })
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  await page.route(url => url.pathname === '/api/logs/voice-draft', route => route.fulfill({ status: 429, contentType: 'application/json',
    body: JSON.stringify({ code: 'VOICE_LIMIT_REACHED', plan: 'free', limit: 5, resetsOn: '2026-11-01' }) }))
  await page.goto('/erfassen')

  await record(page)
  await expect(page.getByTestId('voice-quota-out')).toContainText('Deine 5 Aufnahmen für')
  await expect(page.getByTestId('voice-quota-out')).toContainText('Als Supporter hast du 30 im Monat')
  // Kein toter Knopf im Daumenbereich
  await expect(page.getByTestId('voice-mic')).toHaveCount(0)
  await expect(page.getByTestId('voice-problem')).toHaveCount(0)
  await expect(page.getByTestId('place-here')).toBeVisible()
  await expect.poll(() => events(page)).toContainEqual(['Voice', { step: 'quota', kind: 'out', plan: 'free' }])
})

test('Erstnutzer: ein Mikrofon im Footer mit Text, Beispielsatz darüber', async ({ page }) => {
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  await page.goto('/erfassen')

  const mic = page.locator('footer').getByTestId('voice-mic')
  await expect(mic).toHaveText('Einsprechen')
  await expect(page.getByTestId('voice-mic')).toHaveCount(1)
  await expect(page.getByTestId('voice-note')).toContainText('EnBW, 32 kWh')
  await mic.click()
  await expect(page.getByTestId('voice-sheet')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect.poll(() => events(page)).toContainEqual(['Voice', { step: 'open', entry: 'create', again: false, stage: 'new' }])
})

test('Nach zwei Aufnahmen: nur noch das Symbol, kein Beispiel, Kontingent-Hinweis bleibt', async ({ page }) => {
  await open(page, 'USER', { plan: 'free', limit: 5, remaining: 3, exhausted: false, resetsOn: '2026-11-01' })
  await page.addInitScript(() => localStorage.setItem('voicelog_uses', '2'))
  await page.goto('/erfassen')

  const mic = page.locator('footer').getByTestId('voice-mic')
  await expect(mic).toBeVisible()
  await expect(mic).toHaveText('')
  await expect(mic).toHaveAttribute('aria-label', 'Ladevorgang einsprechen')
  await expect(page.getByTestId('voice-note')).toHaveCount(0)
})

test('Vertraut mit wenig Kontingent: Streifen zeigt nur den Hinweis', async ({ page }) => {
  await open(page, 'USER', { plan: 'free', limit: 5, remaining: 2, exhausted: false, resetsOn: '2026-11-01' })
  await page.addInitScript(() => localStorage.setItem('voicelog_uses', '2'))
  await page.goto('/erfassen')

  await expect(page.getByTestId('voice-quota-low')).toContainText('Noch 2 Aufnahmen im')
  await expect(page.getByTestId('voice-note')).not.toContainText('EnBW')
})

test('Desktop-Modal: Aufnahme-Vollbild liegt über dem Erfassen-Modal', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await open(page, 'ADMIN')
  await page.addInitScript(() => localStorage.setItem('voicelog_consent_seen_v2', 'true'))
  await page.goto('/dashboard')
  await page.getByRole('button', { name: 'Ladevorgang erfassen' }).first().click()
  await page.getByTestId('voice-mic').click()
  const sheet = page.getByTestId('voice-sheet')
  await expect(sheet).toBeVisible()
  // Oberstes Element in der Bildmitte gehört zum Vollbild, nicht zum Modal darunter
  expect(await page.evaluate(() => !!document.elementFromPoint(640, 400)?.closest('[data-testid="voice-sheet"]'))).toBe(true)
})

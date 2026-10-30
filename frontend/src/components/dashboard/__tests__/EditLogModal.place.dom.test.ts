// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: [] })), patch: vi.fn(), delete: vi.fn() },
}))
import api from '../../../api/axios'
import EditLogModal from '../EditLogModal.vue'

const log = {
  id: 'log-1', carId: 'car-1', kwhCharged: 24, costEur: 8.06, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: null, geohash: null, odometerKm: 36179, maxChargingPowerKw: null, socAfterChargePercent: 90,
  socBeforeChargePercent: null, kwhAtVehicle: null, loggedAt: '2026-09-27T12:40:00', routeType: null, tireType: null,
  chargingType: 'DC' as const, isPublicCharging: true, cpoName: null, chargingProviderId: null, dataSource: 'MANUAL',
}
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => vi.clearAllMocks())
const q = (sel: string) => document.body.querySelector<HTMLElement>(sel)

describe('EditLogModal - direkt im Ort (Betreiber eintragen)', () => {
  it('öffnet die Ort-Ansicht und zeigt die Säulen um den gespeicherten Ort', async () => {
    ;(api.get as any).mockImplementation((url: string) => Promise.resolve({ data: url.includes('nearby-stations')
      ? [{ name: 'Tesla Supercharger', known: true, distanceMeters: 40, geohash: 'u1x3k7q', address: 'Stillhorn',
          fastCharging: true, chargePoints: 8, maxAcKw: null, maxDcKw: 250, plugTypes: [], registerId: null }] : [] }))
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log: { ...log, geohash: 'u1x3k7q' }, startSection: 'place' }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))

    await vi.waitFor(() => expect(document.body.textContent).toContain('Tesla Supercharger'))
    const call = (api.get as any).mock.calls.find((c: unknown[]) => String(c[0]).includes('nearby-stations'))
    expect(call[1].params.lat).toBeCloseTo(53.68, 1)
    expect(q('[data-testid="edit-done"]')).not.toBeNull()
  })
})

describe('EditLogModal - Übersicht zeigt die Ladung', () => {
  it('der Ort steht als Kartenkopf über den Kacheln, ein Tipp darauf öffnet den Ort', async () => {
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log: { ...log, geohash: 'u1x3k7q', cpoName: 'Tesla' } }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await nextTick()

    const heads = document.body.querySelectorAll<HTMLElement>('[data-testid="summary-place"]')
    expect(heads).toHaveLength(1)
    // Mit Zeitpunkt im Kopf ist der Ortsname das Tastaturziel, nicht der ganze Kopf (kein Knopf im Knopf)
    expect(heads[0].querySelector('[role="button"]')?.textContent).toContain('Tesla')
    expect(heads[0].style.height).not.toBe('') // mit gespeicherter Zelle: Minimap
    expect(heads[0].textContent).toContain('Tesla')
    heads[0].click()
    await nextTick()
    expect(q('#wizard-place-search')).not.toBeNull()
  })
})

describe('EditLogModal - Ort per Adresse', () => {
  it('übernimmt die gewählte Adresse als Koordinaten ins PATCH', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ json: () => Promise.resolve([
      { place_id: 1, lat: '48.51', lon: '14.50', display_name: 'Billa, Linzer Straße, Freistadt' }]) })))
    ;(api.patch as any).mockResolvedValue({ data: {} })
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await nextTick()

    q('[data-testid="summary-place"]')!.click()
    await nextTick()
    const input = q('#wizard-place-search') as HTMLInputElement
    input.value = 'Linzer Str. 51, Freistadt'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    q('[data-testid="place-search-address"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await vi.waitFor(() => expect(q('[data-testid="place-search-suggestion"]')).not.toBeNull())
    q('[data-testid="place-search-suggestion"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    q('[data-testid="edit-done"]')!.click()
    await nextTick()
    ;[...document.body.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Speichern')?.click()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ latitude: 48.51, longitude: 14.5 })
  })

  it('zeigt eine per Suche gewählte Säule als ausgewählte Kachel', async () => {
    ;(api.get as any).mockImplementation((url: string) => Promise.resolve({ data: url.includes('search-stations')
      ? [{ name: 'Electra', geohash: 'u3b1234', address: 'Westfalendamm 290, Dortmund', fastCharging: true, chargePoints: 4, maxAcKw: null, maxDcKw: 300 }] : [] }))
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await nextTick()
    q('[data-testid="summary-place"]')!.click()
    await nextTick()
    const input = q('#wizard-place-search') as HTMLInputElement
    input.value = 'Electra dortmund'
    input.dispatchEvent(new Event('input'))
    await vi.waitFor(() => expect(q('[data-testid="place-search-station"]')).not.toBeNull())
    q('[data-testid="place-search-station"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    expect(q('[data-testid="place-searched-station"]')).not.toBeNull()
  })

  it('zeigt nach der Adresse die Säulen dort; die Privat-Zeile behält die Adresse, auch nach Wahl einer Säule', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ json: () => Promise.resolve([
      { place_id: 1, lat: '48.51', lon: '14.50', display_name: 'Linzer Straße 51, Freistadt' }]) })))
    ;(api.get as any).mockImplementation((url: string) => Promise.resolve({ data: url.includes('nearby-stations')
      ? [{ name: 'Billa', known: false, distanceMeters: 120, geohash: 'u2dr3ab', address: 'Linzer Straße 49, Freistadt',
          fastCharging: false, chargePoints: 2, maxAcKw: 22, maxDcKw: null, plugTypes: [], registerId: null }] : [] }))
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await nextTick()
    q('[data-testid="summary-place"]')!.click()
    await nextTick()
    const input = q('#wizard-place-search') as HTMLInputElement
    input.value = 'Linzer Str. 51, Freistadt'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    q('[data-testid="place-search-address"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await vi.waitFor(() => expect(q('[data-testid="place-search-suggestion"]')).not.toBeNull())
    q('[data-testid="place-search-suggestion"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))

    const billa = await vi.waitFor(() => {
      const b = [...document.body.querySelectorAll('button')].find(x => x.textContent?.includes('Billa'))
      expect(b).toBeTruthy(); return b!
    })
    expect(q('[data-testid="place-here"]')!.textContent).toContain('Linzer Straße 51, Freistadt')
    billa.click()
    await nextTick()
    expect(q('[data-testid="place-here"]')!.textContent).toContain('Linzer Straße 51, Freistadt')
  })
})

describe('EditLogModal - private Ladung auf öffentlich umstellen', () => {
  const home = { ...log, geohash: 'u33df5', isPublicCharging: false, chargingType: 'AC' as const, costEur: 6, chargingProviderId: 'home-card' }
  const stationsAt = (url: string) => Promise.resolve({ data: url.includes('nearby-stations')
    ? [{ name: 'EnBW Parkhaus', known: true, distanceMeters: 80, geohash: 'u33df50', address: 'Hauptstraße 1',
        fastCharging: false, chargePoints: 2, maxAcKw: 22, maxDcKw: null, plugTypes: [], registerId: null }] : [] })
  const mountAt = async () => {
    ;(api.get as any).mockImplementation(stationsAt)
    app = createApp(defineComponent({ render: () => h(EditLogModal, { log: home }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await nextTick()
    q('[data-testid="summary-place"]')!.click()
  }

  it('zeigt beim Öffnen des Orts aus der Übersicht die Säulen im gespeicherten Feld', async () => {
    await mountAt()

    await vi.waitFor(() => expect(document.body.textContent).toContain('EnBW Parkhaus'))
    const call = (api.get as any).mock.calls.find((c: unknown[]) => String(c[0]).includes('nearby-stations'))
    expect(call[1].params.lat).toBeCloseTo(52.5, 0)
  })

  it('verwirft Heimtarif-Kosten und -Karte, sobald eine Säule gewählt ist', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    await mountAt()
    const station = await vi.waitFor(() => {
      const b = [...document.body.querySelectorAll('button')].find(x => x.textContent?.includes('EnBW Parkhaus'))
      expect(b).toBeTruthy(); return b!
    })
    station.click()
    await nextTick()
    q('[data-testid="edit-done"]')!.click()
    await nextTick()
    ;[...document.body.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Speichern')?.click()
    await nextTick()

    // Ohne Kosten lässt sich nicht speichern: der Nutzer trägt den Preis der Säule ein
    expect(api.patch).not.toHaveBeenCalled()
  })
})

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
})

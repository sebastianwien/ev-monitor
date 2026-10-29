// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

const kaufland = { id: 'c1', providerName: 'Kaufland', label: 'Kaufland', isPrivate: false, acPricePerKwh: 0.29, dcPricePerKwh: 0.44 }
vi.mock('../../../api/axios', () => ({
  default: {
    get: vi.fn((url: string) => Promise.resolve({ data: url === '/logs/price-suggestion'
      ? { costPerKwh: 0.29, chargingProviderId: 'c1', chargingType: 'AC' } : url === '/users/me/charging-providers' ? [kaufland] : [] })),
    patch: vi.fn(), delete: vi.fn(),
  },
}))
vi.mock('../../log-wizard/useVoiceQuota', async () => {
  const { ref } = await import('vue')
  return { useVoiceQuota: () => ({ available: ref(true), load: vi.fn() }) }
})
vi.mock('../../../composables/useIsMobile', () => ({ useIsMobile: () => ref(true) }))
vi.mock('../../../composables/useVoiceRecorder', () => ({ isVoiceSupported: () => true }))
vi.mock('../../../services/analytics', () => ({ analytics: { trackVoice: vi.fn() } }))
// Aufnahme selbst testet useVoiceRecorder; hier zählt nur, was ein Entwurf im Formular anrichtet
let nextDraft: unknown = null
vi.mock('../../log-wizard/VoiceCapture.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { __esModule: true, default: defineComponent({ emits: ['draft'], setup: (_, { emit }) => () =>
    h('button', { 'data-testid': 'voice-mic', onClick: () => emit('draft', nextDraft) }, 'mic') }) }
})
import api from '../../../api/axios'
import EditLogModal from '../EditLogModal.vue'

const log = {
  id: 'log-1', carId: 'car-1', kwhCharged: 24, costEur: 8.06, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: null, geohash: 'u33df5w', odometerKm: 36179, maxChargingPowerKw: null, socAfterChargePercent: 90,
  socBeforeChargePercent: null, kwhAtVehicle: null, loggedAt: '2026-09-27T12:40:00', routeType: null, tireType: null,
  chargingType: 'DC' as const, isPublicCharging: false, cpoName: null, chargingProviderId: null, dataSource: 'TESLA_LIVE',
}
const fields = (over: Record<string, unknown>) => ({
  kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: null, costEur: null, pricePerKwh: null,
  loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
  spokenAddress: null, uncertain: [], ...over,
})
const draft = (over: Record<string, unknown>) => ({ transcript: 'x', fields: fields(over), place: null, chargingProviderId: null,
  usage: { plan: 'admin', limit: null, remaining: null, resetsOn: '2026-11-01' } })

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => vi.clearAllMocks())
const q = (sel: string) => document.body.querySelector<HTMLElement>(sel)
const mount = async () => {
  app = createApp(defineComponent({ render: () => h(EditLogModal, { log }) }))
  app.use(i18n).use(createPinia()); app.directive('haptic', {})
  app.mount(document.body.appendChild(document.createElement('div')))
  await vi.waitFor(() => expect(q('[data-testid="voice-mic"]')).not.toBeNull())
}
const speak = async (d: unknown) => {
  nextDraft = d
  q('[data-testid="voice-mic"]')!.click()
  await vi.waitFor(() => expect(q('[data-testid="edit-voice-changes"]')).not.toBeNull())
}
const save = () => [...document.body.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Speichern')!.click()

const mixedStation = { name: 'Kaufland', known: true, distanceMeters: 30, maxAcKw: 43, maxDcKw: 50, fastCharging: true, chargePoints: 2,
  geohash: 'u33df5w', address: null, plugTypes: ['Typ 2', 'CCS'], registerId: 1, latitude: null, longitude: null }

describe('EditLogModal - Ortswechsel per Sprache', () => {
  it('behält die Ladeart der Ladung und übernimmt die Karte vom letzten Mal dort', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    await mount()
    await speak({ ...draft({}), place: { kind: 'station', station: mixedStation, site: null, cpoName: null } })

    // Das Register darf die gemessene Ladeart nicht umschreiben
    expect(q('[data-testid="edit-voice-change-chargingType"]')).toBeNull()
    expect(q('[data-testid="edit-voice-change-chargingProviderId"]')!.textContent).toContain('Kaufland')

    save()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ chargingType: 'DC', chargingProviderId: 'c1' })
  })

  it('eine vorhandene Karte wird nicht überschrieben', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    // Öffentlich: beim Wechsel von privat fiele die (Heim-)Karte ohnehin weg
    log.chargingProviderId = 'mine' as any
    log.isPublicCharging = true as any
    await mount()
    await speak({ ...draft({}), place: { kind: 'station', station: mixedStation, site: null, cpoName: null } })
    save()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ chargingProviderId: 'mine' })
    log.chargingProviderId = null as any
    log.isPublicCharging = false as any
  })
})

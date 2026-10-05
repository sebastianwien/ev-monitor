// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: [] })), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock('../../../stores/auth', () => ({ useAuthStore: () => ({ isAdmin: true }) }))
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
import { analytics } from '../../../services/analytics'
import EditLogModal from '../EditLogModal.vue'

const log = {
  id: 'log-1', carId: 'car-1', kwhCharged: 24, costEur: 8.06, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: null, geohash: null, odometerKm: 36179, maxChargingPowerKw: null, socAfterChargePercent: 90,
  socBeforeChargePercent: null, kwhAtVehicle: null, loggedAt: '2026-09-27T12:40:00', routeType: null, tireType: null,
  chargingType: 'DC' as const, isPublicCharging: true, cpoName: 'EnBW', chargingProviderId: null, dataSource: 'MANUAL',
}
const fields = (over: Record<string, unknown>) => ({
  kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: null, costEur: null, pricePerKwh: null,
  loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
  spokenAddress: null, uncertain: [], ...over,
})
const draft = (over: Record<string, unknown>) => ({ transcript: 'x', fields: fields(over), place: null, chargingProviderId: null,
  usage: { limit: null, remaining: null, resetsOn: '2026-11-01' } })

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

describe('EditLogModal - per Sprache ändern', () => {
  it('ändert nur Gesagtes, zeigt Vorher/Nachher und speichert es', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    await mount()
    await speak(draft({ odometerKm: 36250, costEur: 9.5 }))

    expect(q('[data-testid="edit-voice-change-odometerKm"]')!.textContent).toMatch(/36.?179.*36.?250/)
    expect(q('[data-testid="edit-voice-change-costEur"]')).not.toBeNull()
    expect(q('[data-testid="edit-voice-change-kwhCharged"]')).toBeNull()

    save()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ odometerKm: 36250, costEur: 9.5, kwhCharged: 24 })
    await vi.waitFor(() => expect(analytics.trackVoice).toHaveBeenCalledWith('saved', { entry: 'edit', corrected: 0 }))
  })

  it('Rückgängig stellt den Stand vor der Sprache wieder her', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    await mount()
    await speak(draft({ odometerKm: 36250 }))
    q('[data-testid="edit-voice-undo"]')!.click()
    await nextTick()
    expect(q('[data-testid="edit-voice-changes"]')).toBeNull()

    save()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ odometerKm: 36179 })
  })

  it('sagt, wenn eine Aufnahme nichts geändert hat', async () => {
    await mount()
    await speak(draft({ odometerKm: 36179 }))
    expect(q('[data-testid="edit-voice-changes"]')!.textContent).toContain('Nichts geändert')
  })

  it('eine zweite Aufnahme überschreibt die erste und zählt nicht als Korrektur', async () => {
    ;(api.patch as any).mockResolvedValue({ data: {} })
    await mount()
    await speak(draft({ socAfter: 85 }))
    await speak(draft({ socAfter: 88 }))
    save()
    await vi.waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect((api.patch as any).mock.calls[0][1]).toMatchObject({ socAfterChargePercent: 88 })
    // die zweite Aufnahme ist selbst Sprache - keine Korrektur
    await vi.waitFor(() => expect(analytics.trackVoice).toHaveBeenCalledWith('saved', { entry: 'edit', corrected: 0 }))
  })
})

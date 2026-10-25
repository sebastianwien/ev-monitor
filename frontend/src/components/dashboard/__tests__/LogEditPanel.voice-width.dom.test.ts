// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: [] })), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock('../../log-wizard/useVoiceQuota', async () => {
  const { ref } = await import('vue')
  return { useVoiceQuota: () => ({ available: ref(true), load: vi.fn() }) }
})
vi.mock('../../../composables/useVoiceRecorder', () => ({ isVoiceSupported: () => true }))
vi.mock('../../log-wizard/VoiceCapture.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { __esModule: true, default: defineComponent({ setup: () => () => h('button', { 'data-testid': 'voice-mic' }, 'mic') }) }
})
import LogEditPanel from '../LogEditPanel.vue'

const log = {
  id: 'log-1', carId: 'car-1', kwhCharged: 24, costEur: 8.06, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: null, geohash: null, odometerKm: 36179, maxChargingPowerKw: null, socAfterChargePercent: 90,
  socBeforeChargePercent: null, kwhAtVehicle: null, loggedAt: '2026-09-27T12:40:00', routeType: null, tireType: null,
  chargingType: 'DC' as const, isPublicCharging: true, cpoName: 'EnBW', chargingProviderId: null, dataSource: 'MANUAL',
}

/** matchMedia, das max-width-Abfragen gegen eine feste Breite beantwortet */
function viewport(width: number) {
  vi.stubGlobal('matchMedia', (query: string) => {
    const max = Number(/max-width:\s*(\d+)px/.exec(query)?.[1] ?? Infinity)
    return { matches: width <= max, media: query, addEventListener: () => {}, removeEventListener: () => {} }
  })
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.unstubAllGlobals() })
const mount = async () => {
  app = createApp(defineComponent({ render: () => h(LogEditPanel, { log }) }))
  app.use(i18n).use(createPinia()); app.directive('haptic', {})
  app.mount(document.body.appendChild(document.createElement('div')))
  await nextTick()
  await new Promise(r => setTimeout(r, 0))
}
const mic = () => document.body.querySelector('[data-testid="voice-mic"]')

describe('LogEditPanel - Spracheingabe nach Breite', () => {
  it('Tablet (900 px): Spracheingabe sichtbar', async () => {
    viewport(900)
    await mount()
    await vi.waitFor(() => expect(mic()).not.toBeNull())
  })

  it('Desktop (1280 px): keine Spracheingabe', async () => {
    viewport(1280)
    await mount()
    expect(mic()).toBeNull()
  })
})

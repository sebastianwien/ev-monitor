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
  id: 'log-1', carId: 'car-1', kwhCharged: 30, costEur: null, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: null, geohash: null, odometerKm: 12000, maxChargingPowerKw: null, socAfterChargePercent: 80,
  socBeforeChargePercent: 30, kwhAtVehicle: null, loggedAt: '2026-08-16T10:00:00', routeType: null, tireType: null,
  chargingType: 'AC' as const, isPublicCharging: true, cpoName: 'EnBW', chargingProviderId: null, dataSource: 'MANUAL',
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const tick = async () => { for (let i = 0; i < 3; i++) { await nextTick(); await new Promise(r => setTimeout(r, 0)) } }

// Kosten fehlten beim Öffnen und werden im Kasten "Noch offen" eingetragen: die Kachel zeigt den Wert, nicht mehr "offen"
describe('LogEditPanel: fehlender Wert nachgetragen', () => {
  it('Kostenkachel zeigt den eingetragenen Betrag', async () => {
    app = createApp(defineComponent({ render: () => h(LogEditPanel, { log }) }))
    app.use(i18n).use(createPinia()); app.directive('haptic', {})
    app.mount(document.body.appendChild(document.createElement('div')))
    await tick()
    const tile = () => document.body.querySelector('[data-testid="summary-cost"]')!.textContent ?? ''
    expect(tile()).not.toMatch(/12[,.]50/)
    const input = document.body.querySelector('#wizard-cost') as HTMLInputElement
    input.value = '12.50'
    input.dispatchEvent(new Event('input'))
    await tick()
    expect(tile()).toMatch(/12[,.]50/)
    // Der Kasten bleibt, solange er offen ist: das Feld verschwindet nicht unter dem Finger
    expect(document.body.querySelector('[data-testid="edit-missing"]')).not.toBeNull()
  })
})

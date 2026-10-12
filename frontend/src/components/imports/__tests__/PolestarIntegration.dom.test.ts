// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

/**
 * Polestar-Testmodus: ohne Verbindung Anleitung + VIN-Feld, mit Verbindung Status-Karte.
 * Verbinden liefert ACTIVE, NOT_SHARED oder WAITLIST - jeder Zustand hat seine eigene Rückmeldung.
 */
const state = {
  connections: [] as unknown[],
  connectResult: { result: 'ACTIVE' } as Record<string, unknown>,
  connectError: null as unknown,
}
const post = vi.fn(() => state.connectError ? Promise.reject(state.connectError) : Promise.resolve({ data: state.connectResult }))
vi.mock('../../../api/axios', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: state.connections })),
    post: (...args: unknown[]) => post(...(args as [])),
    delete: vi.fn(() => Promise.resolve()),
  },
}))
vi.mock('../../../services/analytics', () => ({ analytics: { trackPolestarConnect: vi.fn() } }))
import PolestarIntegration from '../PolestarIntegration.vue'
import { analytics } from '../../../services/analytics'

const car = { id: 'car-1', brand: 'POLESTAR', model: 'Polestar 2', year: 2024, status: 'ACTIVE', isPrimary: true } as never
const connected = {
  id: 'conn-1', carId: 'car-1', vin: 'LPSED3KAXNL123456', status: 'ACTIVE',
  connectedAt: '2026-10-12T08:00:00', lastEventAt: null, snapshotCount: 0, result: 'ACTIVE',
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => {
  vi.clearAllMocks()
  state.connections = []
  state.connectResult = { ...connected }
  state.connectError = null
})

async function mount() {
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({ render: () => h(PolestarIntegration, { cars: [car] }) }))
  app.use(createPinia()).use(i18n)
  app.mount(el)
  await flush()
  return el
}
async function flush() { for (let i = 0; i < 5; i++) await nextTick(); await new Promise(r => setTimeout(r, 0)); await nextTick() }
const byId = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`)

async function typeVin(el: HTMLElement, vin: string) {
  const input = byId(el, 'polestar-vin') as HTMLInputElement
  input.value = vin
  input.dispatchEvent(new Event('input'))
  await nextTick()
}

describe('PolestarIntegration', () => {
  it('ohne Verbindung: Anleitung mit Account-ID, VIN-Feld, Testmodus-Satz', async () => {
    const el = await mount()
    expect(byId(el, 'polestar-step1')!.textContent).toContain('polestar@ev-monitor.net')
    expect(byId(el, 'polestar-step1')!.textContent).toContain('Vehicle usage and status')
    expect(byId(el, 'polestar-vin')).not.toBeNull()
    expect(byId(el, 'polestar-testmode')!.textContent).toContain('keine Position')
    expect(byId(el, 'polestar-status')).toBeNull()
  })

  it('ungültige VIN wird vor dem Absenden abgefangen', async () => {
    const el = await mount()
    await typeVin(el, 'LPSED3KAINL12345')
    byId(el, 'polestar-connect')!.click()
    await flush()
    expect(byId(el, 'polestar-vin-error')).not.toBeNull()
    expect(post).not.toHaveBeenCalled()
  })

  it('ACTIVE: Status-Karte mit Trennen, Analytics success', async () => {
    const el = await mount()
    await typeVin(el, ' lpsed3kaxnl123456 ')
    state.connections = [connected]
    byId(el, 'polestar-connect')!.click()
    await flush()
    expect(post).toHaveBeenCalledWith('/polestar/connect', { carId: 'car-1', vin: 'LPSED3KAXNL123456' })
    expect(byId(el, 'polestar-status')).not.toBeNull()
    expect(byId(el, 'polestar-last-data')!.textContent).toContain('noch keine Daten')
    expect(byId(el, 'polestar-disconnect')).not.toBeNull()
    expect(byId(el, 'polestar-vin')).toBeNull()
    expect(analytics.trackPolestarConnect).toHaveBeenCalledWith('success')
  })

  it('NOT_SHARED: Hinweis mit Retry, Formular bleibt', async () => {
    state.connectResult = { carId: 'car-1', result: 'NOT_SHARED' }
    const el = await mount()
    await typeVin(el, 'LPSED3KAXNL123456')
    byId(el, 'polestar-connect')!.click()
    await flush()
    expect(byId(el, 'polestar-not-shared')!.textContent).toContain('noch nicht')
    expect(byId(el, 'polestar-vin')).not.toBeNull()
    expect(analytics.trackPolestarConnect).toHaveBeenCalledWith('not_shared')
  })

  it('WAITLIST: Hinweis auf belegte Testplätze', async () => {
    state.connectResult = { carId: 'car-1', result: 'WAITLIST' }
    const el = await mount()
    await typeVin(el, 'LPSED3KAXNL123456')
    byId(el, 'polestar-connect')!.click()
    await flush()
    expect(byId(el, 'polestar-waitlist')!.textContent).toContain('Testplätze')
    expect(analytics.trackPolestarConnect).toHaveBeenCalledWith('waitlist')
  })

  it('VIN_TAKEN: sprechender Fehler', async () => {
    state.connectError = { response: { status: 409, data: { error: 'VIN_TAKEN' } } }
    const el = await mount()
    await typeVin(el, 'LPSED3KAXNL123456')
    byId(el, 'polestar-connect')!.click()
    await flush()
    expect(byId(el, 'polestar-error')!.textContent).toContain('anderen Konto')
  })

  it('bestehende Verbindung: direkt Status-Karte', async () => {
    state.connections = [connected]
    const el = await mount()
    expect(byId(el, 'polestar-status')).not.toBeNull()
    expect(byId(el, 'polestar-step1')).toBeNull()
  })
})

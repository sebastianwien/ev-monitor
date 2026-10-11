// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import api from '../../../api/axios'
import { analytics } from '../../../services/analytics'
import PersonalRangeCalculator from '../PersonalRangeCalculator.vue'

const RESULT = {
  winterRangeKm: 320, summerRangeKm: 450, longTripStops: 1, longTripChargingMinutes: 30,
  chargesPerWeek: 1.5, monthlyCostEur: 46, dataLevel: 'PARTIAL',
  assumptions: [
    { key: 'BATTERY', measured: true, value: 77 },
    { key: 'WINTER_CONSUMPTION', measured: true, value: 21.4 },
    { key: 'HIGHWAY_CONSUMPTION', measured: false, value: 19.5, measuredPercent: 50 },
  ],
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; sessionStorage.clear() })

function mount(loggedIn: boolean, data = { logCount: 6796, carCount: 35 }) {
  setActivePinia(createPinia())
  if (loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
  }
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PersonalRangeCalculator, { brand: 'Volkswagen', model: 'ID.3', modelName: 'Volkswagen ID.3', ...data })
  app.use(i18n)
  app.mount(host)
  return host
}

async function flush() { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }

function calculateButton(host: HTMLElement) {
  return host.querySelector('[data-testid="personal-range-calculate"]') as HTMLButtonElement
}

describe('PersonalRangeCalculator', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockResolvedValue({ status: 204, data: '' } as never)
    vi.mocked(analytics.track).mockReset()
  })

  it('guest: the button opens the gate instead of a result', async () => {
    const host = mount(false)
    calculateButton(host).click()
    await flush()

    expect(host.querySelector('[data-testid="inline-gate"]')).not.toBeNull()
    expect(host.textContent).toContain('Winterreichweite')
    expect(api.post).not.toHaveBeenCalled()
    expect(analytics.track).toHaveBeenCalledWith('personal_range_gate_shown', expect.anything())
    expect(sessionStorage.getItem('personalRange:inputs')).not.toBeNull()
  })

  it('gate names the real data behind the model', async () => {
    const host = mount(false)
    calculateButton(host).click()
    await flush()

    const gate = host.querySelector('[data-testid="inline-gate"]')!
    expect(gate.textContent).toContain('6.796')
    expect(gate.textContent).toContain('35')
    expect(gate.textContent).toContain('Kein Passwort')
  })

  it('gate says so when the model has too few cars', async () => {
    const host = mount(false, { logCount: 40, carCount: 2 })
    calculateButton(host).click()
    await flush()

    const text = host.querySelector('[data-testid="inline-gate"]')!.textContent
    expect(text).toContain('noch nicht genügend Messdaten')
    expect(text).toContain('2 Fahrern')
    expect(text).not.toContain('deshalb schätzen wir')
  })

  it('logged-in user gets the result tiles and the assumptions', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const host = mount(true)
    await flush()
    calculateButton(host).click()
    await flush()

    expect(api.post).toHaveBeenCalledWith('/personal-range/Volkswagen/ID.3',
      expect.objectContaining({ commuteKmPerDay: expect.any(Number), homeCharging: true }))
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()
    const result = host.querySelector('[data-testid="personal-range-result"]')!
    expect(result.textContent).toContain('320')
    expect(result.textContent).toContain('46')
    expect(result.querySelectorAll('[data-testid="assumption"]').length).toBe(3)
    expect(result.textContent).toContain('(Datenblatt)')
    expect(result.textContent).toContain('zu 50 % gemessen')
    expect(analytics.track).toHaveBeenCalledWith('personal_range_result', expect.objectContaining({ data_level: 'PARTIAL' }))
  })

  it('prefills the inputs from the stored profile', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: {
      commuteKmPerDay: 72, highwaySharePercent: 60, homeCharging: false, longTripKm: 900 } } as never)
    const host = mount(true)
    await flush()

    const commute = host.querySelector('#pr-commute') as HTMLInputElement
    expect(commute.value).toBe('72')
  })
})

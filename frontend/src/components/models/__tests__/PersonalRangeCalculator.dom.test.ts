// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import api from '../../../api/axios'
import { analytics } from '../../../services/analytics'
import PersonalRangeCalculator from '../PersonalRangeCalculator.vue'
import type { PersonalRangeInputs } from '../../../api/personalRangeService'

const RESULT = {
  winterRangeKm: 320, summerRangeKm: 450, longTripStops: 1, longTripChargingMinutes: 30,
  chargesPerWeek: 1.5, chargesPerWeekSummer: 1, chargesPerWeekWinter: 1.5, monthlyCostEur: 46, dataLevel: 'PARTIAL',
  assumptions: [
    { key: 'BATTERY', measured: true, value: 77 },
    { key: 'WINTER_CONSUMPTION', measured: true, value: 21.4 },
    { key: 'HIGHWAY_CONSUMPTION', measured: false, value: 19.5, measuredPercent: 50 },
    { key: 'OWN_PRICE', measured: true, value: 0.28 },
  ],
}
const INPUTS: PersonalRangeInputs = { commuteKmPerDay: 40, highwaySharePercent: 30, homeCharging: true, longTripKm: 500, pricePerKwh: 0.28 }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; sessionStorage.clear() })

function mount(loggedIn: boolean, data = { logCount: 6796, carCount: 35 }, openCount = 0) {
  setActivePinia(createPinia())
  if (loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
  }
  const state = reactive({ profile: null as PersonalRangeInputs | null, incomplete: 0 })
  const Host = defineComponent({
    setup: () => () => h(PersonalRangeCalculator, {
      brand: 'Volkswagen', model: 'ID.3', modelName: 'Volkswagen ID.3', inputs: INPUTS, ...data, openCount,
      onProfile: (p: PersonalRangeInputs) => { state.profile = p },
      onIncomplete: () => { state.incomplete++ },
    }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n)
  app.mount(host)
  return { host, state }
}

async function flush() { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }

function requestButton(host: HTMLElement) {
  return host.querySelector('[data-testid="personal-range-calculate"]') as HTMLButtonElement
}

describe('PersonalRangeCalculator', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockResolvedValue({ status: 204, data: '' } as never)
    vi.mocked(analytics.track).mockReset()
  })

  it('guest: placeholder tiles carry no figure, so nothing can be unblurred in the console', async () => {
    const { host } = mount(false)
    await flush()

    const placeholder = host.querySelector('[data-testid="personal-range-placeholder"]')!
    expect(placeholder.querySelectorAll('dd').length).toBe(4)
    for (const dd of placeholder.querySelectorAll('dd')) expect(dd.textContent).not.toMatch(/\d/)
    expect(host.querySelector('[data-testid="personal-range-result"]')).toBeNull()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('guest: says before the gate what the result rests on and what the mail is for', async () => {
    const { host } = mount(false)
    await flush()

    const intro = host.querySelector('[data-testid="personal-range-intro"]')!.textContent
    expect(intro).toContain('6.796')
    expect(intro).toContain('35')
    const reasons = host.querySelectorAll('[data-testid="personal-range-reason"]')
    expect(reasons.length).toBe(3)
    expect(host.textContent).toContain('Kein Passwort')
  })

  it('guest without data: the intro names the manufacturer figure instead of drivers', async () => {
    const { host } = mount(false, { logCount: 0, carCount: 0 })
    await flush()

    expect(host.querySelector('[data-testid="personal-range-intro"]')!.textContent).toContain('Herstellerwert')
  })

  it('guest: the button opens the gate instead of a result and keeps the inputs for after the login', async () => {
    const { host } = mount(false)
    requestButton(host).click()
    await flush()

    expect(host.querySelector('[data-testid="inline-gate"]')).not.toBeNull()
    expect(api.post).not.toHaveBeenCalled()
    expect(analytics.track).toHaveBeenCalledWith('personal_range_gate_shown', expect.anything())
    expect(JSON.parse(sessionStorage.getItem('personalRange:inputs')!)).toEqual(INPUTS)
  })

  it('with open questions the button sends the reader back to them instead of opening the gate', async () => {
    const { host, state } = mount(false, undefined, 2)
    await flush()

    expect(host.querySelector('[data-testid="personal-range-open"]')!.textContent).toContain('Noch 2 Fragen offen')
    requestButton(host).click()
    await flush()

    expect(state.incomplete).toBe(1)
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()
    expect(sessionStorage.getItem('personalRange:inputs')).toBeNull()
  })

  it('gate names the real data behind the model', async () => {
    const { host } = mount(false)
    requestButton(host).click()
    await flush()

    const gate = host.querySelector('[data-testid="inline-gate"]')!
    expect(gate.textContent).toContain('6.796')
    expect(gate.textContent).toContain('35')
    expect(gate.textContent).toContain('Kein Passwort')
  })

  it('gate says so when the model has too few cars', async () => {
    const { host } = mount(false, { logCount: 40, carCount: 2 })
    requestButton(host).click()
    await flush()

    const text = host.querySelector('[data-testid="inline-gate"]')!.textContent
    expect(text).toContain('noch nicht genügend Messdaten')
    // cars, not drivers: the hero counts drivers, and the two numbers differ
    expect(text).toContain('2 Autos')
  })

  it('logged-in user gets the result tiles and the assumptions, the own price among them', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { host } = mount(true)
    await flush()
    requestButton(host).click()
    await flush()

    expect(api.post).toHaveBeenCalledWith('/personal-range/Volkswagen/ID.3', INPUTS)
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()
    expect(host.querySelector('[data-testid="personal-range-placeholder"]')).toBeNull()
    const result = host.querySelector('[data-testid="personal-range-result"]')!
    expect(result.textContent).toContain('450')
    expect(result.textContent).toContain('320')
    expect(result.textContent).toContain('46')
    // weekly charging for the commute under each season
    expect(result.textContent).toContain('Pendeln: 1× laden pro Woche')
    expect(result.textContent).toContain('Pendeln: 1,5× laden pro Woche')
    expect(result.querySelectorAll('[data-testid="assumption"]').length).toBe(4)
    expect(result.textContent).toContain('(Datenblatt)')
    expect(result.textContent).toContain('zu 50 % gemessen')
    expect(result.textContent).toContain('(deine Angabe)')
    expect(analytics.track).toHaveBeenCalledWith('personal_range_result', expect.objectContaining({ data_level: 'PARTIAL' }))
  })

  it('hands the stored profile back to the page instead of keeping it to itself', async () => {
    const stored = { commuteKmPerDay: 72, highwaySharePercent: 60, homeCharging: false, longTripKm: 900 }
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: stored } as never)
    const { state } = mount(true)
    await flush()

    expect(state.profile).toEqual(stored)
    expect(api.post).not.toHaveBeenCalled()
  })

  it('back from a login redirect: calculates with the pending inputs and asks for the mail once', async () => {
    sessionStorage.setItem('personalRange:inputs', JSON.stringify({ ...INPUTS, longTripKm: 800 }))
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { state } = mount(true)
    await flush()

    expect(state.profile).toEqual({ ...INPUTS, longTripKm: 800 })
    expect(api.post).toHaveBeenCalledWith('/personal-range/Volkswagen/ID.3', { ...INPUTS, longTripKm: 800 }, { params: { email: true } })
    expect(sessionStorage.getItem('personalRange:inputs')).toBeNull()
  })
})

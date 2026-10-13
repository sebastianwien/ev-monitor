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
  winterRangeKm: 320, summerRangeKm: 450, longTripStops: 1, longTripChargingMinutes: 30, longTripTotalMinutes: 305,
  chargesPerWeek: 1.5, chargesPerWeekSummer: 0.3, chargesPerWeekWinter: 1.5, monthlyCostEur: 46, dataLevel: 'PARTIAL',
  assumptions: [
    { key: 'BATTERY', measured: true, value: 77 },
    { key: 'WINTER_CONSUMPTION', measured: true, value: 21.4 },
    { key: 'HIGHWAY_CONSUMPTION', measured: false, value: 19.5, measuredPercent: 50 },
    { key: 'OWN_PRICE', measured: true, value: 0.28 },
    { key: 'CHARGING_POWER', measured: false, value: 104 },
    { key: 'TRAVEL_SPEED', measured: false, value: 110 },
  ],
}
// Gültiges JWT-Format (Payload: {"userId":"u1","sub":"neu@example.com"}), Signatur egal
const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJ1MSIsInN1YiI6Im5ldUBleGFtcGxlLmNvbSJ9.x'
const INPUTS: PersonalRangeInputs = { commuteKmPerDay: 40, commuteDaysPerWeek: 3, highwaySharePercent: 30, homeCharging: true, longTripKm: 500, pricePerKwh: 0.28 }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; sessionStorage.clear(); vi.useRealTimers() })

function mount(loggedIn: boolean, data: { logCount: number; carCount: number; petrol?: { pricePerLiter: number; litersPer100km: number; category: string | null }; fastPricePerKwh?: number } = { logCount: 6796, carCount: 35 }, openCount = 0, inputs: Partial<PersonalRangeInputs> = {}) {
  setActivePinia(createPinia())
  if (loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
  }
  const state = reactive({ profile: null as PersonalRangeInputs | null, incomplete: 0, home: null as boolean | null, price: undefined as number | null | undefined, inputs: { ...INPUTS, ...inputs }, openCount })
  const Host = defineComponent({
    setup: () => () => h(PersonalRangeCalculator, {
      brand: 'Volkswagen', model: 'ID.3', modelName: 'Volkswagen ID.3', inputs: state.inputs, ...data, openCount: state.openCount,
      onProfile: (p: PersonalRangeInputs) => { state.profile = p },
      onIncomplete: () => { state.incomplete++ },
      onHome: (v: boolean) => { state.home = v },
      onPrice: (v: number | null) => { state.price = v },
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

  it('guest: names the four questions, the placeholders carry no figure, so nothing can be unblurred in the console', async () => {
    const { host } = mount(false)
    await flush()

    const questions = [...host.querySelectorAll('[data-testid="personal-range-questions"] li')].map(li => li.textContent?.trim())
    expect(questions).toEqual(['1Wie weit komme ich im Winter?', '2Wie oft muss ich laden?', '3Wie viele Stopps auf 500 km?', '4Was kostet mich das im Monat?'])
    const placeholder = host.querySelector('[data-testid="personal-range-placeholder"]')!
    expect(placeholder.querySelectorAll('dd').length).toBe(4)
    for (const dd of placeholder.querySelectorAll('dd')) expect(dd.textContent).not.toMatch(/\d/)
    expect(host.querySelector('[data-testid="personal-range-result"]')).toBeNull()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('guest: says what the result rests on and that it assumes home charging', async () => {
    const { host } = mount(false)
    await flush()

    expect(host.textContent).toContain('Aus 6.796 echten Ladevorgängen, nicht aus Herstellerangaben.')
    expect(host.textContent).toContain('Gerechnet mit Laden zu Hause, das kannst du danach ändern.')
    expect(host.textContent).toContain('Deine 4 Antworten sind bereit')
  })

  it('guest with few cars: no promise of measured data', async () => {
    const { host } = mount(false, { logCount: 12, carCount: 2 })
    await flush()

    expect(host.textContent).toContain('Aus 12 Ladevorgängen von erst 2 Autos. Wo Messwerte fehlen, rechnen wir mit Herstellerangaben und markieren das.')
  })

  it('open sliders: the lock counts them and the button sends the reader back instead of opening the gate', async () => {
    const { host, state } = mount(false, undefined, 2)
    await flush()

    expect(host.querySelector('[data-testid="personal-range-open"]')!.textContent).toContain('Noch 2 Regler offen')
    requestButton(host).click()
    await flush()
    expect(state.incomplete).toBe(1)
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()
    expect(analytics.track).toHaveBeenCalledWith('personal_range_unlock_click', { model: 'Volkswagen ID.3', open: 2 })
  })

  it('guest: the button turns the box into the gate and keeps the inputs for after the login', async () => {
    const { host } = mount(false)
    await flush()

    requestButton(host).click()
    await flush()

    expect(host.querySelector('[data-testid="inline-gate"]')).not.toBeNull()
    expect(host.textContent).toContain('Wir nutzen deine Adresse nur für dein Ergebnis und dein Konto.')
    expect(JSON.parse(sessionStorage.getItem('personalRange:inputs')!)).toEqual(INPUTS)
    expect(api.post).not.toHaveBeenCalled()
  })

  it('signed in with every slider answered: the four answers right away, no gate', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { host } = mount(true)
    await flush()

    const result = host.querySelector('[data-testid="personal-range-result"]')!
    const text = (key: string) => result.querySelector(`[data-testid="answer-${key}"]`)!.textContent?.replace(/\s+/g, ' ')
    expect(text('winter')).toMatch(/Im Winter kommst du ca\. 320\skm weit, im Sommer ca\. 450\skm\./)
    expect(text('winter')).toContain('100 %')
    expect(text('winter')).toContain('10 %')
    // 0.3 a week in summer: every 3 weeks; 1.5 in winter: every 4 days; 40 km on 3 days
    expect(text('rhythm')).toContain('Im Sommer alle 3 Wochen')
    expect(text('rhythm')).toContain('Im Winter alle 4 Tage')
    expect(text('rhythm')).toContain('120 km Pendelstrecke pro Woche')
    expect(text('stops')).toMatch(/Auf deiner Urlaubsfahrt über 500\skm wirst du wahrscheinlich einmal laden\./)
    expect(text('stops')).toContain('5 h 5 min unterwegs')
    expect(text('stops')).toContain('davon 30 min laden')
    // the sentences carry the questions, no thin headline above them
    expect(result.textContent).not.toContain('Was kostet mich das im Monat?')
    expect(text('cost')).toMatch(/Es kostet dich 46\s€ im Monat, dein Auto aufzuladen\./)
    expect(host.querySelectorAll('[data-testid="assumption"]').length).toBe(6)
    const own = [...host.querySelectorAll('[data-testid="assumption"]')].map(li => li.textContent).find(x => x?.includes('Dein Strompreis'))
    expect(own).toContain('28,0 ct/kWh')
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()
  })

  it('result: the wallbox answer and the own price go back to the page', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { host, state } = mount(true)
    await flush()

    ;(host.querySelector('[data-testid="wallbox-no"]') as HTMLButtonElement).click()
    expect(state.home).toBe(false)
    expect(analytics.track).toHaveBeenCalledWith('personal_range_wallbox', { model: 'Volkswagen ID.3', home: false })
    // own price set: a way back to the community price
    const reset = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Zurück zum Community-Preis'))!
    reset.click()
    expect(state.price).toBeNull()
  })

  it('result: a changed input is recalculated, debounced', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { state } = mount(true)
    await flush()
    const calls = vi.mocked(api.post).mock.calls.length

    vi.useFakeTimers()
    state.inputs.homeCharging = false
    state.inputs.homeCharging = true
    state.inputs.highwaySharePercent = 50
    await nextTick()
    vi.advanceTimersByTime(500)
    vi.useRealTimers()
    await flush()

    expect(vi.mocked(api.post).mock.calls.length).toBe(calls + 1)
    expect((vi.mocked(api.post).mock.calls[vi.mocked(api.post).mock.calls.length - 1][1] as PersonalRangeInputs).highwaySharePercent).toBe(50)
  })

  it('an older answer arriving last never overwrites a newer one', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: RESULT } as never)
    const { host, state } = mount(true)
    await flush()
    let slow!: (v: unknown) => void
    vi.mocked(api.post)
      .mockImplementationOnce(() => new Promise(r => { slow = r }) as never)
      .mockResolvedValueOnce({ data: { ...RESULT, winterRangeKm: 280 } } as never)

    vi.useFakeTimers()
    state.inputs.homeCharging = false
    await nextTick(); vi.advanceTimersByTime(500)
    state.inputs.homeCharging = true
    await nextTick(); vi.advanceTimersByTime(500)
    vi.useRealTimers()
    await flush()
    slow({ data: { ...RESULT, winterRangeKm: 999 } })
    await flush()

    expect(host.querySelector('[data-testid="answer-winter"]')!.textContent).toContain('280 km')
  })

  it('the charging rhythm reads with the inputs of its own calculation and names the commute it rests on', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: RESULT } as never)
    const { host, state } = mount(true)
    await flush()
    vi.mocked(api.post).mockImplementationOnce(() => new Promise(() => {}) as never)

    state.inputs.homeCharging = false
    await flush()

    // still the home-charging result: days, not stops, until the new answer is there
    const rhythm = host.querySelector('[data-testid="answer-rhythm"]')!.textContent
    expect(rhythm).toContain('alle 4 Tage')
    expect(rhythm).toContain('120 km Pendelstrecke pro Woche')
  })

  it('after the gate one calculation with the mail, not a second one without', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
      .mockResolvedValue({ data: RESULT } as never)
    const { host } = mount(false)
    await flush()
    requestButton(host).click()
    await flush()
    const email = host.querySelector('input[type="email"]') as HTMLInputElement
    email.value = 'neu@example.com'; email.dispatchEvent(new Event('input'))
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()
    const code = host.querySelector('input[autocomplete="one-time-code"]') as HTMLInputElement
    code.value = '123456'; code.dispatchEvent(new Event('input'))
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush(); await flush()

    const calcs = vi.mocked(api.post).mock.calls.filter(c => String(c[0]).startsWith('/personal-range/'))
    expect(calcs.length).toBe(1)
    expect(calcs[0][2]).toEqual({ params: { email: true } })
  })

  it('the same rhythm in summer and winter reads as one line', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...RESULT, chargesPerWeekSummer: 1.5, chargesPerWeekWinter: 1.6 } } as never)
    const { host } = mount(true)
    await flush()

    const rhythm = host.querySelector('[data-testid="answer-rhythm"]')!.textContent?.replace(/\s+/g, ' ')
    expect(rhythm).toContain('Normal wirst du ca. alle 4 Tage laden müssen.')
    expect(rhythm).not.toContain('Im Sommer')
  })

  it('cost: what a comparable petrol car would cost a month, and the saving', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: RESULT } as never)
    const { host } = mount(true, { logCount: 6796, carCount: 35, petrol: { pricePerLiter: 1.8, litersPer100km: 6.8, category: 'Kompakt' } }, 0, { annualKm: 12000 })
    await flush()

    // 1.000 km a month × 6.8 l × 1.80 € = 122 €; the EV 46 €
    const cost = host.querySelector('[data-testid="answer-cost"]')!.textContent?.replace(/\s+/g, ' ')
    expect(cost).toMatch(/Ein vergleichbarer Benziner kostet rund 122\s€, du sparst 76\s€ im Monat\./)
    // the petrol car of the same vehicle class, named
    expect(cost).toMatch(/Kompakt 6,8 l\/100 km Benzin bei 1,80\s€ je Liter/)
    // 46 € × 12 / 12.000 km = 4,60 € per 100 km
    expect(cost).toMatch(/4,60\s€\/100km bei 12\.000\skm im Jahr/)
    expect(cost).not.toContain('Tankerkönig')
  })

  it('the holiday trip names what its charging stops cost, at the price without a subscription', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...RESULT, longTripStops: 2 } } as never)
    const { host } = mount(true, { logCount: 6796, carCount: 35, fastPricePerKwh: 0.65 }, 0, { netBatteryKwh: 59 })
    await flush()

    // 2 stops × 59 × 0.7 / 0.95 = 43.5 kWh × 0.65 = 56.5 € → 57 €
    expect(host.querySelector('[data-testid="answer-stops"]')!.textContent).toMatch(/rund 57\s€ fürs Laden/)
  })

  it('cost: no petrol comparison without a fuel price (outside Germany)', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: RESULT } as never)
    const { host } = mount(true, undefined, 0, { annualKm: 12000 })
    await flush()

    expect(host.querySelector('[data-testid="answer-cost"]')!.textContent).not.toContain('Benziner')
  })

  it('hands the stored profile back to the page instead of keeping it to itself', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: { ...INPUTS, commuteKmPerDay: 80 } } as never)
    const { state } = mount(true, undefined, 3)
    await flush()

    expect(state.profile?.commuteKmPerDay).toBe(80)
  })

  it('back from a login redirect: calculates with the pending inputs and asks for the mail once', async () => {
    sessionStorage.setItem('personalRange:inputs', JSON.stringify({ ...INPUTS, longTripKm: 800 }))
    vi.mocked(api.post).mockResolvedValue({ data: RESULT } as never)
    const { host, state } = mount(true)
    await flush()

    expect(state.profile?.longTripKm).toBe(800)
    expect(vi.mocked(api.post).mock.calls[0][2]).toEqual({ params: { email: true } })
    expect(sessionStorage.getItem('personalRange:inputs')).toBeNull()
    expect(host.querySelector('[data-testid="personal-range-result"]')).not.toBeNull()
  })
})

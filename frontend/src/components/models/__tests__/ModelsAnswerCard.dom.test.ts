// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import ModelsAnswerCard from '../ModelsAnswerCard.vue'
import { NEEDS_EMPTY, type NeedsInput } from '../../../utils/needsCheck'
import type { PersonalRangeRankingEntry } from '../../../api/personalRangeService'
import type { TopModelPreview } from '../../../api/publicModelService'

vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))

function entry(model: string, over: Partial<PersonalRangeRankingEntry> = {}): PersonalRangeRankingEntry {
  return {
    brand: 'B', model, summerRangeKm: 400, winterRangeKm: 300, longTripStops: 1, longTripTotalMinutes: 360,
    chargesPerWeekSummer: 0.7, chargesPerWeekWinter: 1.0, monthlyCostEur: 60, monthlyKm: 1250,
    zeroStopReachKm: 330, oneStopReachKm: 610, twoStopsReachKm: 890, ...over,
  }
}
function preview(model: string): TopModelPreview {
  return {
    brand: 'B', model, brandDisplayName: 'Brand', modelDisplayName: `Brand ${model}`, modelUrlSlug: model, logCount: 100,
    avgConsumptionKwhPer100km: 18, minRealConsumptionKwhPer100km: null, maxRealConsumptionKwhPer100km: null,
    minWltpConsumptionKwhPer100km: 16, maxWltpConsumptionKwhPer100km: 16, avgCostPerKwh: null, category: 'SUV',
    categoryDisplayName: 'SUV', realRangeKm: 350, contributorCount: 6,
  } as TopModelPreview
}
// Six models: two make 600 km with one stop, the others need two; M6 is the cheapest
const ENTRIES = [
  entry('M1', { longTripStops: 1, winterRangeKm: 340, monthlyCostEur: 70 }),
  entry('M2', { longTripStops: 1, winterRangeKm: 300, monthlyCostEur: 66 }),
  entry('M3', { longTripStops: 2, winterRangeKm: 280, monthlyCostEur: 62 }),
  entry('M4', { longTripStops: 2, winterRangeKm: 260, monthlyCostEur: 58 }),
  entry('M5', { longTripStops: 2, winterRangeKm: 240, monthlyCostEur: 56 }),
  entry('M6', { longTripStops: 2, winterRangeKm: 200, monthlyCostEur: 52 }),
]
const ANSWERS: NeedsInput = { ...NEEDS_EMPTY, annualKm: 15_000, highwayKm: 4_000, dailyKm: 40, commuteDays: 5, longestTripKm: 600 }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(locked: boolean, start: NeedsInput = ANSWERS, models: TopModelPreview[] = ENTRIES.map(e => preview(e.model))) {
  setActivePinia(createPinia())
  const state = reactive({ needs: { ...start } as NeedsInput, price: undefined as number | null | undefined })
  const Host = defineComponent({
    setup: () => () => h(ModelsAnswerCard, {
      modelValue: state.needs, 'onUpdate:modelValue': (v: NeedsInput) => { state.needs = v },
      entries: ENTRIES, models, price: 0.242, locked,
      petrolPricePerLiter: 2.15, modelHref: (m: TopModelPreview) => `/modelle/Brand/${m.modelUrlSlug}`,
      onPrice: (v: number | null) => { state.price = v },
    }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n).use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { render: () => null } }] }))
  app.mount(host)
  return { host, state }
}
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ')

describe('ModelsAnswerCard', () => {
  it('guest: free spans over all models and one gate that names how many fit, no model and no figure of one', async () => {
    const { host } = mount(true)
    await nextTick()
    expect(text(host.querySelector('[data-testid="ranking-answer-range"]'))).toMatch(/200\skm bis 340\skm/)
    expect(text(host.querySelector('[data-testid="ranking-answer-stops"]'))).toContain('1- bis 2-mal')
    expect(text(host.querySelector('[data-testid="ranking-answer-cost"]'))).toContain('zu Hause geladen für 24,2 ct/kWh')
    expect(text(host.querySelector('[data-testid="ranking-gate-lead"]'))).toContain('2 Modelle schaffen deine 600 km mit 1 Stopp')
    expect(host.querySelector('[data-testid="top-five"]')).toBeNull()
    expect(host.querySelector('[data-testid="personal-range-tune"]')).toBeNull()
    expect(text(host)).not.toContain('Brand M1')
  })

  it('signed in: the profile as one line, wallbox and price, and the 5 best by what matters most', async () => {
    const { host, state } = mount(false)
    await nextTick()
    expect(text(host.querySelector('[data-testid="profile-summary"]'))).toContain('wichtig: Weit kommen')
    expect(host.querySelector('[data-testid="personal-range-tune"]')).not.toBeNull()
    const names = () => [...host.querySelectorAll('[data-testid="top-five-row"] a')].map(a => a.textContent)
    // far: fewest stops, then the larger winter range
    expect(names()).toEqual(['Brand M1', 'Brand M2', 'Brand M3', 'Brand M4', 'Brand M5'])
    expect(host.querySelector('[data-testid="top-five-row"] a')!.getAttribute('href')).toBe('/modelle/Brand/M1')
    // marked is what the order rests on: far = stops and winter range, so trip and charging, first
    const marked = () => [...host.querySelector('[data-testid="top-five-row"]')!.querySelectorAll('[role="cell"][data-marked]')].map(c => c.getAttribute('data-testid'))
    expect(marked()).toEqual(['top-five-trip', 'top-five-charge'])

    ;(host.querySelector('[data-testid="priority-cost"]') as HTMLButtonElement).click()
    await nextTick()
    expect(state.needs.priority).toBe('cost')
    expect(names()).toEqual(['Brand M6', 'Brand M5', 'Brand M4', 'Brand M3', 'Brand M2'])
    // the month column comes first and is marked
    expect(host.querySelector('[data-testid="top-five-row"] [role="cell"]')!.getAttribute('data-testid')).toBe('top-five-month')
    expect(marked()).toEqual(['top-five-month'])
  })

  it('signed in: no wallbox moves the answers to charging on the road', async () => {
    const { host, state } = mount(false)
    await nextTick()
    ;(host.querySelector('[data-testid="wallbox-no"]') as HTMLButtonElement).click()
    await nextTick()
    expect(state.needs.homeCharging).toBe(false)
    expect(text(host.querySelector('[data-testid="ranking-answer-cost"]'))).toContain('unterwegs geladen')
  })

  it('signed in without answers: the sliders stay open while the reader answers them', async () => {
    const { host, state } = mount(false, NEEDS_EMPTY)
    await nextTick()
    state.needs = { ...state.needs, dailyKm: 40 }
    await nextTick()
    expect(host.querySelector('[data-testid="profile-summary"]')).toBeNull()
    expect(host.querySelector('[data-testid="slider-needs-commute"]')).not.toBeNull()
  })

  it('phone: a guest who comes back with answers sees them as one line, the sliders behind "Angaben ändern"', async () => {
    const original = window.matchMedia
    window.matchMedia = ((q: string) => ({ matches: q.includes('max-width'), media: q, addEventListener() {}, removeEventListener() {} })) as never
    try {
      const { host } = mount(true)
      await nextTick()
      expect(text(host.querySelector('[data-testid="profile-summary"]'))).toContain('15.000 km im Jahr')
    } finally {
      window.matchMedia = original
    }
  })

  it('charging: one statement, no season, in the answer and in the top 5', async () => {
    const { host } = mount(false)
    await nextTick()
    const answer = text(host.querySelector('[data-testid="ranking-answer-charge"]'))
    expect(answer).not.toMatch(/Winter|Sommer/)
    const cell = host.querySelector('[data-testid="top-five-charge"]')!
    expect(cell.querySelector('small')).toBeNull()
    expect(text(cell)).not.toMatch(/Winter|Sommer/)
  })

  it('price: no hint under the slider while the reader keeps the drivers\' average', async () => {
    const { host } = mount(false)
    await nextTick()
    expect(text(host.querySelector('[data-testid="personal-range-tune"]'))).not.toContain('Startwert')
  })

  it('top 5: a model name that carries the brand names it once', async () => {
    const models = ENTRIES.map(e => preview(e.model))
    models[0] = { ...models[0], brandDisplayName: 'Polestar', modelDisplayName: 'Polestar Polestar 3', modelUrlSlug: 'Polestar_3' }
    const { host } = mount(false, ANSWERS, models)
    await nextTick()
    expect(host.querySelector('[data-testid="top-five-row"] a')!.textContent).toBe('Polestar 3')
  })
})

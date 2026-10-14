// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, ref, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import ModelRankingRow from '../ModelRankingRow.vue'
import { useModelRanking, NEEDS_STORAGE_KEY } from '../../../composables/useModelRanking'
import { personalRangeService, type PersonalRangeRankingEntry } from '../../../api/personalRangeService'
import { buildLadderAxis } from '../../../utils/ladderScale'
import type { TopModelPreview } from '../../../api/publicModelService'

function model(over: Partial<TopModelPreview> & { brandDisplayName: string, modelUrlSlug: string }): TopModelPreview {
  return {
    brand: over.brandDisplayName.toUpperCase(),
    model: over.modelUrlSlug.toUpperCase(),
    modelDisplayName: `${over.brandDisplayName} ${over.modelUrlSlug.replace(/_/g, ' ')}`,
    logCount: 100,
    avgConsumptionKwhPer100km: 18,
    minRealConsumptionKwhPer100km: null,
    maxRealConsumptionKwhPer100km: null,
    minWltpConsumptionKwhPer100km: 16,
    maxWltpConsumptionKwhPer100km: 16,
    avgWltpConsumptionKwhPer100km: 16,
    avgCostPerKwh: null,
    category: 'SEDAN',
    categoryDisplayName: 'Limousine',
    realRangeKm: 350,
    summerConsumptionKwhPer100km: null,
    winterConsumptionKwhPer100km: null,
    ...over,
  }
}

vi.mock('../../../api/personalRangeService', () => ({ personalRangeService: { ranking: vi.fn() } }))

/** The server's figures for the model: every 14 days in summer, every 10 in winter, no stop on 400 km */
function figures(m: TopModelPreview, over: Partial<PersonalRangeRankingEntry> = {}): PersonalRangeRankingEntry {
  return {
    brand: m.brand, model: m.model, summerRangeKm: 400, winterRangeKm: 300, longTripStops: 0, longTripTotalMinutes: 220,
    chargesPerWeekSummer: 0.5, chargesPerWeekWinter: 0.67, monthlyCostEur: 25, monthlyKm: 460, oneStopReachKm: 610, twoStopsReachKm: 890, ...over,
  }
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; localStorage.clear() })

type Answers = { dailyKm: number | null; commuteDays: number | null; longestTripKm: number | null; homeCharging: boolean | null; annualKm?: number | null }
/** Anna: 40 km on 3 days, charging at home, 400 km longest trip */
const ANNA: Answers = { dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: true }

/** One ranked row for the model with the given answers and server figures (none: the server left the model out) */
async function mount(m: TopModelPreview, opts: { locked?: boolean; expanded?: boolean; answers?: Answers; fuelPrice?: number; figures?: Partial<PersonalRangeRankingEntry> | null } = {}) {
  setActivePinia(createPinia())
  const answers = opts.answers ?? ANNA
  vi.mocked(personalRangeService.ranking).mockResolvedValue(opts.figures === null ? [] : [figures(m, opts.figures)])
  localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify({ annualKm: null, highwayKm: null, ...answers }))
  const { ranked, cost, needsReady } = useModelRanking(ref([m]))
  if (opts.fuelPrice) cost.value = { ...cost.value, fuelPricePerLiter: opts.fuelPrice }
  await new Promise(resolve => setTimeout(resolve))
  const events: string[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(ModelRankingRow, {
    item: ranked.value[0], axis: buildLadderAxis([18, 16], 'kWh/100km'), sort: 'efficient',
    expanded: opts.expanded ?? false, inCompare: false, canAddCompare: true, href: '/modelle/x/y',
    price: 0.35, mainValue: 'consumption', answered: answers.longestTripKm != null || answers.dailyKm != null,
    needsReady: needsReady.value, longestTripKm: answers.longestTripKm, maxStops: 0, fuel: 'petrol',
    locked: opts.locked ?? false,
    onUnlock: () => events.push('unlock'),
  })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  return { host, events }
}

const hint = (host: HTMLElement) => host.querySelector('[data-testid="needs-hint"]')!.textContent ?? ''
const CAR = model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', typicalRangeMinKm: 400, typicalRangeMaxKm: 400 })
const detail = (host: HTMLElement) => host.querySelector('[id^="mr-detail-"]')!.textContent ?? ''

describe('ModelRankingRow', () => {
  it('signed in: the hint carries the server figures', async () => {
    const { host } = await mount(CAR)
    expect(hint(host)).toContain('Fürs Pendeln alle 14 Tage laden, im Winter alle 10 Tage')
    expect(hint(host)).toContain('400 km: ohne Stopp')
  })

  it('the trip line names the stops and the whole travel time, as on the model page', async () => {
    const { host } = await mount(CAR, { figures: { longTripStops: 1, longTripTotalMinutes: 245 } })
    expect(hint(host)).toContain('400 km: 1 Stopp, 4 h 5 min unterwegs')
  })

  it('the longest trip alone: dot and stops, no commute line', async () => {
    const { host } = await mount(CAR, { answers: { dailyKm: null, commuteDays: null, longestTripKm: 400, homeCharging: null }, figures: { longTripStops: 1 } })
    expect(hint(host)).not.toContain('Pendeln')
    expect(hint(host)).toContain('400 km: 1 Stopp')
    expect(host.querySelector('.mr-hint i')).not.toBeNull()
  })

  it('the commute alone: no dot, no trip verdict', async () => {
    const { host } = await mount(CAR, { answers: { ...ANNA, longestTripKm: null } })
    expect(hint(host)).toContain('Fürs Pendeln')
    expect(hint(host)).not.toContain('Stopp')
    expect(host.querySelector('.mr-hint i')).toBeNull()
  })

  it('without answers the row shows model values only: no dot, no hint', async () => {
    const { host } = await mount(CAR, { answers: { dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null } })
    expect(host.querySelector('.mr-hint i')).toBeNull()
    expect(host.querySelector('[data-testid="needs-hint"]')).toBeNull()
  })

  it('a model the server left out says so, with a grey dot', async () => {
    const { host } = await mount(CAR, { figures: null })
    expect(hint(host)).toContain('noch nicht bewertbar')
    expect(host.querySelector('.mr-hint i')!.className).toContain('bg-gray-300')
  })

  it('0 km commute: no charging needed, without a dangling verb', async () => {
    const { host } = await mount(CAR, { answers: { ...ANNA, dailyKm: 0 }, figures: { chargesPerWeekSummer: 0, chargesPerWeekWinter: 0 } })
    expect(hint(host)).toContain('Fürs Pendeln kein Laden nötig')
    expect(hint(host)).not.toContain('nötig laden')
  })

  it('the month\'s cost and its distance come from the server', async () => {
    const { host } = await mount(CAR, { expanded: true })
    expect(detail(host)).toContain('bei 460 km im Monat')
    expect(detail(host)).toContain('Kosten im Monat25€')
  })

  it('the month\'s cost is the third line under the name: the figure signed in, a lock for guests', async () => {
    const open = await mount(CAR)
    expect(hint(open.host)).toMatch(/25\s€ im Monat/)
    app?.unmount()
    const { host } = await mount(CAR, { locked: true })
    expect(host.querySelector('[data-testid="needs-locked-cost"]')).not.toBeNull()
    expect(hint(host)).not.toMatch(/\d+\s€ im Monat/)
  })

  it('the open row shows the reach with one and two stops from the server', async () => {
    const { host } = await mount(CAR, { expanded: true })
    expect(detail(host)).toContain('Mit 1 Stopp / 2 Stopps610 / 890km')
  })

  it('yearly savings count the commute', async () => {
    const { host } = await mount(CAR, { expanded: true, fuelPrice: 1.8 })
    expect(detail(host)).toMatch(/pro Jahr fürs Pendeln/)
  })

  describe('locked (guest)', () => {
    it('the month\'s cost and the yearly savings are personal: a guest sees the lock, never the figure', async () => {
      const { host } = await mount(CAR, { expanded: true, locked: true, fuelPrice: 1.8 })
      const open = host.querySelector('[id^="mr-detail-"]')!
      expect(open.querySelector('[data-testid="fact-locked"]')).not.toBeNull()
      expect(open.textContent).not.toContain('25€')
      expect(open.textContent).not.toMatch(/pro Jahr fürs Pendeln/)
    })

    it('keeps the verdict but no figure: the trip is possible without a stop', async () => {
      const { host } = await mount(CAR, { locked: true })
      expect(hint(host)).toContain('400 km ohne Ladestopp')
      // the dot judges the trip: the commute line above keeps only its invisible gutter
      expect(host.querySelector('[data-testid="needs-locked-commute"] i')!.className).toContain('invisible')
      expect(hint(host)).not.toMatch(/Stopps/)
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-green-600')
    })

    it('shows where the commute figure stands, with a stand-in that is never the real value', async () => {
      const { host } = await mount(CAR, { locked: true })
      expect(host.querySelector('[data-testid="needs-locked-commute"]')).not.toBeNull()
      expect(host.textContent).not.toContain('alle 14 Tage')
    })

    it('names a needed stop without counting it', async () => {
      const { host } = await mount(CAR, { locked: true, figures: { longTripStops: 2 } })
      expect(hint(host)).toContain('400 km nicht ohne Ladestopp')
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-orange-500')
    })

    it('the open row explains the gate and its button asks the page to unlock', async () => {
      const { host, events } = await mount(CAR, { locked: true, expanded: true })
      const open = host.querySelector('[id^="mr-detail-"]')!
      expect(open.textContent).toContain('400 km?')
      expect(open.textContent).not.toMatch(/Tage laden/)
      ;(open.querySelector('[data-testid="row-unlock"]') as HTMLButtonElement).click()
      await nextTick()
      expect(events).toEqual(['unlock'])
    })
  })
})

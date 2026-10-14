// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, ref, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import ModelRankingRow from '../ModelRankingRow.vue'
import { useModelRanking } from '../../../composables/useModelRanking'
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

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; localStorage.clear() })

type Answers = { dailyKm: number | null; commuteDays: number | null; longestTripKm: number | null; homeCharging: boolean | null; annualKm?: number | null }
/** Anna: 40 km on 3 days, charging at home, 400 km longest trip */
const ANNA: Answers = { dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: true }

/** One ranked row for the model with the given answers */
function mount(m: TopModelPreview, opts: { locked?: boolean; expanded?: boolean; answers?: Answers; fuelPrice?: number } = {}) {
  setActivePinia(createPinia())
  const models = ref([m])
  const { ranked, needs, cost } = useModelRanking(models)
  const answers = opts.answers ?? ANNA
  needs.value = { annualKm: null, highwayKm: null, ...answers, maxStops: 0 }
  if (opts.fuelPrice) cost.value = { ...cost.value, fuelPricePerLiter: opts.fuelPrice }
  const events: string[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(ModelRankingRow, {
    item: ranked.value[0], axis: buildLadderAxis([18, 16], 'kWh/100km'), sort: 'efficient',
    expanded: opts.expanded ?? false, inCompare: false, canAddCompare: true, href: '/modelle/x/y',
    price: 0.35, mainValue: 'consumption', answered: answers.longestTripKm != null || answers.dailyKm != null,
    longestTripKm: answers.longestTripKm, maxStops: 0, fuel: 'petrol',
    locked: opts.locked ?? false,
    onUnlock: () => events.push('unlock'),
  })
  app.use(i18n)
  app.mount(host)
  return { host, events }
}

const hint = (host: HTMLElement) => host.querySelector('[data-testid="needs-hint"]')!.textContent ?? ''
const WINTER_OK = model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_3', typicalRangeMinKm: 620, typicalRangeMaxKm: 700, winterRangeMinKm: 520, winterRangeMaxKm: 600 })
const WINTER_STOP = model({ brandDisplayName: 'Fiat', modelUrlSlug: '500e', typicalRangeMinKm: 250, typicalRangeMaxKm: 300, winterRangeMinKm: 180, winterRangeMaxKm: 220 })

/** Anna's example: winter 300 km, typical 400 km, one battery */
const ANNA_CAR = model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', typicalRangeMinKm: 400, typicalRangeMaxKm: 400, winterRangeMinKm: 300, winterRangeMaxKm: 300 })

describe('ModelRankingRow', () => {
  it('signed in: the hint carries the figures', async () => {
    const { host } = mount(WINTER_OK)
    await nextTick()
    expect(hint(host)).toContain('Fürs Pendeln')
    expect(hint(host)).toContain('400 km: ohne Stopp')
  })

  it('the commute line follows the commute rule: 120 km a week, every 14 days, every 10 in winter', async () => {
    const { host } = mount(ANNA_CAR)
    await nextTick()
    // typical 0.6 × 400 = 240 km a charge → 0.5 a week → every 14 days; winter 180 km → 0.67 → every 10 days
    expect(hint(host)).toContain('Fürs Pendeln alle 14 Tage laden, im Winter alle 10 Tage')
  })

  it('several batteries: a span, named as such', async () => {
    const { host } = mount(model({ brandDisplayName: 'Kia', modelUrlSlug: 'EV3', typicalRangeMinKm: 300, typicalRangeMaxKm: 400, winterRangeMinKm: 300, winterRangeMaxKm: 400 }))
    await nextTick()
    // 120 / 180 = 0.67 → 10 days, 120 / 240 = 0.5 → 14 days
    expect(hint(host)).toContain('Fürs Pendeln alle 10 bis 14 Tage laden, je nach Akku')
  })

  it('the longest trip alone: dot and stops, no commute line', async () => {
    const { host } = mount(ANNA_CAR, { answers: { dailyKm: null, commuteDays: null, longestTripKm: 400, homeCharging: null } })
    await nextTick()
    expect(hint(host)).not.toContain('Pendeln')
    expect(hint(host)).toContain('400 km: 1 Stopp')
    expect(host.querySelector('.mr-hint i')).not.toBeNull()
  })

  it('the commute alone: no dot, no trip verdict', async () => {
    const { host } = mount(ANNA_CAR, { answers: { ...ANNA, longestTripKm: null } })
    await nextTick()
    expect(hint(host)).toContain('Fürs Pendeln')
    expect(hint(host)).not.toContain('Stopp')
    expect(host.querySelector('.mr-hint i')).toBeNull()
  })

  it('without answers the row shows model values only: no dot, no hint', async () => {
    const { host } = mount(ANNA_CAR, { answers: { dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null } })
    await nextTick()
    expect(host.querySelector('.mr-hint i')).toBeNull()
    expect(host.querySelector('[data-testid="needs-hint"]')).toBeNull()
  })

  it('0 km commute: no charging needed, without a dangling verb', async () => {
    const { host } = mount(ANNA_CAR, { answers: { ...ANNA, dailyKm: 0 } })
    await nextTick()
    expect(hint(host)).toContain('Fürs Pendeln kein Laden nötig')
    expect(hint(host)).not.toContain('nötig laden')
  })

  it('the month\'s cost rests on the yearly mileage when answered, else on the commute over 46 weeks', async () => {
    const commute = mount(ANNA_CAR, { expanded: true })
    await nextTick()
    // 40 km × 3 days × 46 weeks / 12
    expect(commute.host.querySelector('[id^="mr-detail-"]')!.textContent).toContain('bei 460 km im Monat')
    app?.unmount()
    const yearly = mount(ANNA_CAR, { expanded: true, answers: { ...ANNA, annualKm: 12_000 } })
    await nextTick()
    expect(yearly.host.querySelector('[id^="mr-detail-"]')!.textContent).toContain('bei 1.000 km im Monat')
  })

  it('the month\'s cost is the third line under the name: the figure signed in, a lock for guests', async () => {
    const open = mount(ANNA_CAR)
    await nextTick()
    expect(hint(open.host)).toMatch(/\d+\s€ im Monat/)
    app?.unmount()
    const { host } = mount(ANNA_CAR, { locked: true })
    await nextTick()
    expect(host.querySelector('[data-testid="needs-locked-cost"]')).not.toBeNull()
    expect(hint(host)).not.toMatch(/\d+\s€ im Monat/)
  })

  it('yearly savings count the commute', async () => {
    const { host } = mount(ANNA_CAR, { expanded: true, fuelPrice: 1.8 })
    await nextTick()
    expect(host.querySelector('[id^="mr-detail-"]')!.textContent).toMatch(/pro Jahr fürs Pendeln/)
  })

  describe('locked (guest)', () => {
    it('the month\'s cost and the yearly savings are personal: a guest sees the lock, never the figure', async () => {
      const open = mount(ANNA_CAR, { expanded: true, fuelPrice: 1.8 })
      await nextTick()
      const cost = [...open.host.querySelectorAll('dl > div')].find(d => d.textContent!.includes('Kosten im Monat'))!
      const real = cost.querySelector('dd')!.textContent!.trim()
      app?.unmount()
      const { host } = mount(ANNA_CAR, { expanded: true, locked: true, fuelPrice: 1.8 })
      await nextTick()
      const detail = host.querySelector('[id^="mr-detail-"]')!
      expect(detail.querySelector('[data-testid="fact-locked"]')).not.toBeNull()
      expect(detail.textContent).not.toContain(real)
      expect(detail.textContent).not.toMatch(/pro Jahr fürs Pendeln/)
    })

    it('keeps the verdict but no figure: the trip is possible without a stop, winter included', async () => {
      const { host } = mount(WINTER_OK, { locked: true })
      await nextTick()
      expect(hint(host)).toContain('400 km ohne Ladestopp, auch im Winter')
      // the dot judges the trip: the commute line above keeps only its invisible gutter
      expect(host.querySelector('[data-testid="needs-locked-commute"] i')!.className).toContain('invisible')
      expect(hint(host)).not.toMatch(/Stopps/)
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-green-600')
    })

    it('shows where the commute figure stands, with a stand-in that is never the real value', async () => {
      const open = mount(WINTER_OK)
      await nextTick()
      const real = hint(open.host).match(/Fürs Pendeln (.+?)(400 km|$)/)![1].trim()
      const { host } = mount(WINTER_OK, { locked: true })
      await nextTick()
      expect(host.querySelector('[data-testid="needs-locked-commute"]')).not.toBeNull()
      expect(host.textContent).not.toContain(real)
    })

    it('names the winter stop without counting it', async () => {
      const { host } = mount(WINTER_STOP, { locked: true })
      await nextTick()
      expect(hint(host)).toContain('400 km im Winter nicht ohne Ladestopp')
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-orange-500')
    })

    it('without winter data the label drops the winter claim', async () => {
      const { host } = mount(model({ brandDisplayName: 'MG', modelUrlSlug: 'MG4', typicalRangeMinKm: 500, typicalRangeMaxKm: 560 }), { locked: true })
      await nextTick()
      expect(hint(host)).toContain('400 km ohne Ladestopp')
    })

    it('the open row explains the gate and its button asks the page to unlock', async () => {
      const { host, events } = mount(WINTER_OK, { locked: true, expanded: true })
      await nextTick()
      const detail = host.querySelector('[id^="mr-detail-"]')!
      expect(detail.textContent).toContain('400 km?')
      expect(detail.textContent).not.toMatch(/Tage laden/)
      ;(detail.querySelector('[data-testid="row-unlock"]') as HTMLButtonElement).click()
      await nextTick()
      expect(events).toEqual(['unlock'])
    })
  })
})

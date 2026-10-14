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
    chargesPerWeekSummer: 0.5, chargesPerWeekWinter: 0.67, monthlyCostEur: 25, monthlyKm: 460, zeroStopReachKm: 330, oneStopReachKm: 610, twoStopsReachKm: 890, ...over,
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
    onCompare: () => events.push('compare'),
    onAssumptions: () => events.push('assumptions'),
  })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  return { host, events }
}

const hint = (host: HTMLElement) => host.querySelector('[data-testid="needs-hint"]')!.textContent ?? ''
const CAR = model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', typicalRangeMinKm: 400, typicalRangeMaxKm: 400 })
const detail = (host: HTMLElement) => host.querySelector('[id^="mr-detail-"]')!.textContent ?? ''
const forYou = (host: HTMLElement) => host.querySelector('[data-testid="for-you"]')
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ')

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

  describe('open row', () => {
    it('answers the reader first: charging, the longest trip and the month\'s cost, as on the model page', async () => {
      const { host } = await mount(CAR, { expanded: true })
      const box = text(forYou(host))
      expect(box).toContain('Alle 14 Tage')
      expect(box).toContain('im Winter alle 10 Tage')
      expect(box).toContain('Urlaubsfahrt 400 km')
      expect(box).toContain('Ohne Stopp')
      expect(box).toContain('3 h 40 min unterwegs')
      expect(box).toMatch(/25\s€/)
      expect(box).toContain('bei 460 km im Monat')
    })

    it('without answers there is nothing personal to show', async () => {
      const { host } = await mount(CAR, { expanded: true, answers: { dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null } })
      expect(forYou(host)).toBeNull()
    })

    it('the range is the figure of the column, the variant\'s own range only as an addition', async () => {
      const { host } = await mount(CAR, { expanded: true })
      const range = text(host.querySelector('[data-testid="detail-range"]'))
      expect(range).toContain('400 km')
      expect(range).toContain('350 km')
      expect(range).not.toContain('noch zu wenige Fahrten')
    })

    it('the stop ladder names its start: the motorway without a stop, then one and two stops', async () => {
      const { host } = await mount(CAR, { expanded: true })
      const ladder = text(host.querySelector('[data-testid="detail-ladder"]'))
      expect(ladder).toContain('Autobahn')
      expect(ladder).toMatch(/ohne Stopp\s*330 km.*1 Stopp\s*610 km.*2 Stopps\s*890 km/)
    })

    it('both prices say what they are, the mixed price links to the assumptions', async () => {
      const { host, events } = await mount(CAR, { expanded: true })
      expect(detail(host)).toContain('dein Mischpreis')
      expect(detail(host)).toContain('was Fahrer dieses Modells im Schnitt zahlen')
      ;(host.querySelector('[data-testid="detail-assumptions"]') as HTMLButtonElement).click()
      expect(events).toEqual(['assumptions'])
    })

    it('a single driver\'s values are flagged above the data sheet', async () => {
      const { host } = await mount(model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', contributorCount: 1 }), { expanded: true })
      expect(host.querySelector('[data-testid="single-driver-note"]')).not.toBeNull()
    })

    it('ends with one way on: the model page and what only it has; no photo, no compare button', async () => {
      const { host } = await mount(CAR, { expanded: true })
      const open = host.querySelector('[id^="mr-detail-"]')!
      const link = open.querySelector('a[href="/modelle/x/y"]')!
      expect(link.textContent).toContain('Modellseite VW ID.3')
      expect(open.textContent).toContain('Bewertungen')
      expect(open.querySelector('[data-testid="detail-photo"]')).toBeNull()
      expect(open.querySelector('[aria-pressed]')).toBeNull()
    })
  })

  it('the phone has the compare toggle in the row itself', async () => {
    const { host, events } = await mount(CAR)
    const toggle = host.querySelector('[data-testid="compare-phone"]') as HTMLButtonElement
    expect(toggle.getAttribute('aria-pressed')).toBe('false')
    toggle.click()
    expect(events).toEqual(['compare'])
  })

  it('the month\'s cost is the third line under the name: the figure signed in, a lock for guests', async () => {
    const open = await mount(CAR)
    expect(hint(open.host)).toMatch(/25\s€ im Monat/)
    app?.unmount()
    const { host } = await mount(CAR, { locked: true })
    expect(host.querySelector('[data-testid="needs-locked-cost"]')).not.toBeNull()
    expect(hint(host)).not.toMatch(/\d+\s€ im Monat/)
  })

  it('yearly savings count the commute', async () => {
    const { host } = await mount(CAR, { expanded: true, fuelPrice: 1.8 })
    expect(text(forYou(host))).toMatch(/pro Jahr günstiger als Benziner, fürs Pendeln/)
  })

  describe('locked (guest)', () => {
    it('the three answers are personal: a guest sees each behind a lock, never the figure', async () => {
      const { host } = await mount(CAR, { expanded: true, locked: true, fuelPrice: 1.8 })
      const box = forYou(host)!
      expect(box.querySelectorAll('[data-testid="for-you-locked"]')).toHaveLength(3)
      expect(text(box)).not.toMatch(/25\s€/)
      expect(text(box)).not.toContain('alle 14 Tage')
      expect(text(box)).not.toContain('3 h 40 min')
      expect(text(box)).not.toMatch(/pro Jahr/)
    })

    it('keeps the verdict but no figure: the trip is possible without a stop', async () => {
      const { host } = await mount(CAR, { locked: true })
      expect(hint(host)).toContain('400 km ohne Ladestopp')
      // the dot judges the trip: the commute line above keeps only its invisible gutter
      expect(host.querySelector('[data-testid="needs-locked-commute"] i')!.className).toContain('invisible')
      expect(hint(host)).not.toContain('3 h 40 min')
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-green-600')
      expect(host.querySelector('[data-testid="needs-hint-trip"] svg')).not.toBeNull()
    })

    it('shows where the commute figure stands, with a stand-in that is never the real value', async () => {
      const { host } = await mount(CAR, { locked: true })
      expect(host.querySelector('[data-testid="needs-locked-commute"]')).not.toBeNull()
      expect(host.textContent).not.toContain('alle 14 Tage')
    })

    it('names a needed stop positively, without counting it', async () => {
      const { host } = await mount(CAR, { locked: true, figures: { longTripStops: 2 } })
      expect(hint(host)).toContain('400 km mit Ladestopp')
      expect(hint(host)).not.toContain('nicht ohne')
      expect(hint(host)).not.toContain('2 Stopps')
      expect(host.querySelector('[data-testid="needs-hint-trip"] i')!.className).toContain('bg-orange-500')
    })

    it('all three lines under the name follow one pattern: label, stand-in, lock', async () => {
      const { host } = await mount(CAR, { locked: true })
      for (const id of ['needs-locked-commute', 'needs-hint-trip', 'needs-locked-cost']) {
        expect(host.querySelector(`[data-testid="${id}"] svg`), id).not.toBeNull()
      }
    })

    it('the open row has one gate: what the answers are, one button, what happens with the mail', async () => {
      const { host, events } = await mount(CAR, { locked: true, expanded: true })
      const box = text(forYou(host))
      expect(box).toContain('Deine Antworten für den VW ID.3: wie oft du laden musst, wie viele Stopps auf 400 km und was es im Monat kostet.')
      expect(box).toContain('Kein Passwort')
      expect(host.querySelectorAll('[data-testid="row-unlock"]')).toHaveLength(1)
      ;(host.querySelector('[data-testid="row-unlock"]') as HTMLButtonElement).click()
      await nextTick()
      expect(events).toEqual(['unlock'])
    })
  })
})

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

const CAR = model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', typicalRangeMinKm: 400, typicalRangeMaxKm: 400 })
const detail = (host: HTMLElement) => host.querySelector('[id^="mr-detail-"]')!.textContent ?? ''
const forYou = (host: HTMLElement) => host.querySelector('[data-testid="for-you"]')
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ')

describe('ModelRankingRow', () => {
  it('the cost cell prices at the list price and names what the drivers pay', async () => {
    const { host } = await mount(model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_3', avgCostPerKwh: 0.296 }))
    const cell = host.querySelector('[data-testid="cost-cell"]')!.textContent!.replace(/\s+/g, ' ')
    expect(cell).toContain('Fahrer zahlen Ø 29,6 ct/kWh')
  })

  it('the closed row stays free of personal figures, with answers, signed in or not', async () => {
    for (const locked of [false, true]) {
      const { host } = await mount(CAR, { locked })
      expect(host.querySelector('[data-testid="needs-hint"]')).toBeNull()
      expect(host.querySelector('.mr-hint i')).toBeNull()
      app?.unmount(); app = null; document.body.innerHTML = ''
    }
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
      // no stand-in that reads like a figure for this model, nothing blurred
      expect(text(box).split('400 km').join('').replace('ID.3', '')).not.toMatch(/\d/)
      expect(box.querySelector('[class*="blur"]')).toBeNull()
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

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
    mainValue: 'consumption', answered: answers.longestTripKm != null || answers.dailyKm != null,
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
const forYou = (host: HTMLElement) => host.querySelector('[data-testid="for-you"]')
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ')

describe('ModelRankingRow', () => {
  describe('closed row', () => {
    it('names the model once, the brand not repeated, as a link to the model page', async () => {
      const { host } = await mount(model({ brandDisplayName: 'Renault', modelUrlSlug: 'Renault_5' }))
      const link = host.querySelector<HTMLAnchorElement>('[data-testid="row-name"]')!
      expect(link.getAttribute('href')).toBe('/modelle/x/y')
      expect(text(link).trim()).toBe('Renault 5')
      app?.unmount(); app = null; document.body.innerHTML = ''
      const tesla = await mount(model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_3', contributorCount: 42, logCount: 6627 }))
      expect(text(tesla.host.querySelector('[data-testid="row-name"]')).trim()).toBe('Tesla Model 3')
      expect(text(tesla.host.querySelector('[data-testid="row-sub"]'))).toContain('Mittelklasse · 42 Fahrer · 6.627 Ladevorgänge')
    })

    it('the range with the motorway ladder: without a stop and with one', async () => {
      const { host } = await mount(model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', typicalRangeMinKm: 340, typicalRangeMaxKm: 340 }))
      const cell = text(host.querySelector('[data-testid="range-cell"]'))
      expect(cell).toContain('340 km')
      expect(cell).toContain('Autobahn ohne Stopp bis 330, 1 Stopp bis 610')
    })

    it('one consumption bar, drivers against WLTP below it', async () => {
      const { host } = await mount(model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', avgConsumptionKwhPer100km: 20 }))
      const cell = host.querySelector('[data-testid="consumption-cell"]')!
      expect(cell.querySelectorAll('[role="img"]')).toHaveLength(1)
      expect(text(cell)).toMatch(/Fahrer 20,0 · WLTP 16,0 kWh\/100\s?km/)
    })

    it('the cost cell: electricity at the list price, the petrol car, what the drivers pay', async () => {
      const { host } = await mount(model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_3', avgCostPerKwh: 0.296 }), { fuelPrice: 1.8 })
      const lines = [...host.querySelector('[data-testid="cost-cell"]')!.children].map(c => text(c).trim())
      expect(lines).toHaveLength(3)
      expect(lines[0]).toMatch(/^Strom \d+,\d\d\s€$/)
      expect(lines[1]).toMatch(/^Benziner \d+,\d\d\s€$/)
      expect(lines[2]).toBe('Fahrer zahlen Ø 29,6 ct/kWh')
    })

    it('a photo comes with a magnifier that opens it large with its credit; without a photo, initials and no magnifier', async () => {
      const { host } = await mount(model({ brandDisplayName: 'Opel', modelUrlSlug: 'Corsa-e', model: 'CORSA_E' }))
      const zoom = host.querySelector<HTMLButtonElement>('[data-testid="row-zoom"]')!
      expect(zoom.getAttribute('aria-label')).toContain('Opel Corsa-e')
      zoom.click()
      await nextTick()
      const { useLightbox } = await import('../../../composables/useLightbox')
      const { current, close } = useLightbox()
      expect(current.value?.src).toBe('/model-images/CORSA_E.jpg')
      expect(current.value?.credit?.text).toContain('Alexander Migl')
      close()
      app?.unmount(); app = null; document.body.innerHTML = ''
      const plain = await mount(CAR)
      expect(plain.host.querySelector('[data-testid="row-zoom"]')).toBeNull()
      expect(text(plain.host.querySelector('[data-testid="row-initials"]')).trim()).toBe('VW')
    })

    it('compare sits at the row end on the desktop and in the open row on the phone', async () => {
      const { host, events } = await mount(CAR, { expanded: true })
      const desk = host.querySelector<HTMLButtonElement>('[data-testid="compare-row"]')!
      const phone = host.querySelector<HTMLButtonElement>('[data-testid="compare-open"]')!
      expect(desk.getAttribute('aria-pressed')).toBe('false')
      desk.click()
      phone.click()
      expect(events).toEqual(['compare', 'compare'])
    })
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
      // one statement, at the winter figure: holds the whole year
      expect(box).toContain('Alle 10 Tage')
      expect(box).not.toMatch(/Winter|14 Tage/)
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

    it('what the drivers measure, as one paragraph', async () => {
      const { host } = await mount(model({
        brandDisplayName: 'VW', modelUrlSlug: 'ID.3', avgConsumptionKwhPer100km: 20, summerConsumptionKwhPer100km: 19, winterConsumptionKwhPer100km: 22,
        typicalRangeMinKm: 340, typicalRangeMaxKm: 340, winterRangeMinKm: 200, winterRangeMaxKm: 310, avgCostPerKwh: 0.296, logCount: 6627, carCount: 43,
      }), { expanded: true })
      const p = text(host.querySelector('[data-testid="detail-measured"]'))
      expect(p).toMatch(/Verbrauch 20,0 kWh\/100\s?km \(Sommer 19,0, Winter 22,0\), WLTP 16,0 kWh\/100\s?km/)
      expect(p).toContain('Reichweite 340 km, im Winter 200 bis 310 km')
      expect(p).toContain('Ø Ladepreis der Fahrer 29,6 ct/kWh')
      expect(p).toContain('6.627 Ladevorgänge von 43 Autos')
    })

    it('the expert line holds what the closed row leaves out, each part only with a value', async () => {
      const { host } = await mount(model({
        brandDisplayName: 'VW', modelUrlSlug: 'ID.3', avgConsumptionKwhPer100km: 20, summerConsumptionKwhPer100km: 19, winterConsumptionKwhPer100km: 22,
        minNetCapacityKwh: 75, maxNetCapacityKwh: 82,
      }), { expanded: true })
      const line = text(host.querySelector('[data-testid="detail-expert"]'))
      expect(line).toContain('Autobahn 2 Stopps bis 890 km')
      expect(line).toMatch(/Winter \+\d+ % Verbrauch/)
      expect(line).toContain('25 % über WLTP')
      expect(line).toContain('Akku 75 und 82 kWh')
      app?.unmount(); app = null; document.body.innerHTML = ''
      const bare = await mount(model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', avgConsumptionKwhPer100km: 16 }), { expanded: true, figures: null })
      expect(bare.host.querySelector('[data-testid="detail-expert"]')).toBeNull()
    })

    it('a single driver\'s values are flagged above the data sheet', async () => {
      const { host } = await mount(model({ brandDisplayName: 'VW', modelUrlSlug: 'ID.3', contributorCount: 1 }), { expanded: true })
      expect(host.querySelector('[data-testid="single-driver-note"]')).not.toBeNull()
    })

    it('ends with one way on: the model page and what only it has; no photo', async () => {
      const { host } = await mount(CAR, { expanded: true })
      const open = host.querySelector('[id^="mr-detail-"]')!
      const link = open.querySelector('a[href="/modelle/x/y"]')!
      expect(link.textContent).toContain('Modellseite VW ID.3')
      expect(open.textContent).toContain('Bewertungen')
      expect(open.querySelector('[data-testid="detail-photo"]')).toBeNull()
    })
  })

  it('yearly savings count the commute', async () => {
    const { host } = await mount(CAR, { expanded: true, fuelPrice: 1.8 })
    expect(text(forYou(host))).toMatch(/pro Jahr günstiger als Benziner, fürs Pendeln/)
  })

  describe('locked (guest)', () => {
    it('a guest gets no answers and no gate in the row: the one gate sits in chapter 1', async () => {
      const { host } = await mount(CAR, { expanded: true, locked: true, fuelPrice: 1.8 })
      expect(forYou(host)).toBeNull()
      expect(host.querySelector('[data-testid="row-unlock"]')).toBeNull()
      expect(host.querySelector('[data-testid="detail-measured"]')).not.toBeNull()
    })
  })
})

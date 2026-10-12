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

/** One ranked row for the model; the needs check runs with 40 km a day and the given longest trip */
function mount(m: TopModelPreview, opts: { locked?: boolean; expanded?: boolean; longestTripKm?: number } = {}) {
  setActivePinia(createPinia())
  const models = ref([m])
  const { ranked, needs } = useModelRanking(models)
  needs.value = { dailyKm: 40, longestTripKm: opts.longestTripKm ?? 400, homeCharging: true, maxStops: 0 }
  const events: string[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(ModelRankingRow, {
    item: ranked.value[0], axis: buildLadderAxis([18, 16], 'kWh/100km'), sort: 'efficient',
    expanded: opts.expanded ?? false, inCompare: false, canAddCompare: true, href: '/modelle/x/y',
    price: 0.35, mainValue: 'consumption', dailyKm: 40, longestTripKm: opts.longestTripKm ?? 400, maxStops: 0, fuel: 'petrol',
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

describe('ModelRankingRow', () => {
  it('signed in: the hint carries the figures', async () => {
    const { host } = mount(WINTER_OK)
    await nextTick()
    expect(hint(host)).toMatch(/Tage laden/)
    expect(hint(host)).toContain('400 km: ohne Stopp')
  })

  describe('locked (guest)', () => {
    it('keeps the verdict but no figure: the trip is possible without a stop, winter included', async () => {
      const { host } = mount(WINTER_OK, { locked: true })
      await nextTick()
      expect(hint(host)).toBe('400 km ohne Ladestopp, auch im Winter')
      expect(hint(host)).not.toMatch(/Tage|Stopps/)
      expect(host.querySelector('.mr-hint i')!.className).toContain('bg-green-600')
    })

    it('names the winter stop without counting it', async () => {
      const { host } = mount(WINTER_STOP, { locked: true })
      await nextTick()
      expect(hint(host)).toBe('400 km im Winter nicht ohne Ladestopp')
      expect(host.querySelector('.mr-hint i')!.className).toContain('bg-orange-500')
    })

    it('without winter data the label drops the winter claim', async () => {
      const { host } = mount(model({ brandDisplayName: 'MG', modelUrlSlug: 'MG4', typicalRangeMinKm: 500, typicalRangeMaxKm: 560 }), { locked: true })
      await nextTick()
      expect(hint(host)).toBe('400 km ohne Ladestopp')
    })

    it('the open row explains the gate and its button asks the page to unlock', async () => {
      const { host, events } = mount(WINTER_OK, { locked: true, expanded: true })
      await nextTick()
      const detail = host.querySelector('[id^="mr-detail-"]')!
      expect(detail.textContent).toContain('nach Bestätigung deiner Mail')
      expect(detail.textContent).not.toMatch(/Tage laden/)
      ;(detail.querySelector('[data-testid="row-unlock"]') as HTMLButtonElement).click()
      await nextTick()
      expect(events).toEqual(['unlock'])
    })
  })
})

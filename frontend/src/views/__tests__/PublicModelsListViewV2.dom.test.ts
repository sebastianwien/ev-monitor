// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { analytics } from '../../services/analytics'
import { NEEDS_STORAGE_KEY } from '../../composables/useModelRanking'
import { personalRangeService, type PersonalRangeRankingEntry } from '../../api/personalRangeService'
import type { TopModelPreview } from '../../api/publicModelService'
import PublicModelsListViewV2 from '../PublicModelsListViewV2.vue'

const { stub, MODELS } = vi.hoisted(() => {
  const models: import('../../api/publicModelService').TopModelPreview[] = Array.from({ length: 61 }, (_, i) => ({
    brand: 'BRAND', model: `M${i}`, brandDisplayName: 'Brand', brandSlug: 'Brand', modelUrlSlug: `M${i}`, modelDisplayName: `Brand M${i}`,
    logCount: 1000 - i, avgConsumptionKwhPer100km: 15 + i / 10,
    minRealConsumptionKwhPer100km: null, maxRealConsumptionKwhPer100km: null,
    minWltpConsumptionKwhPer100km: 15, maxWltpConsumptionKwhPer100km: 15, avgWltpConsumptionKwhPer100km: 15,
    avgCostPerKwh: 0.3, category: i % 2 ? 'SUV' : 'COMPACT', categoryDisplayName: i % 2 ? 'SUV' : 'Kompakt', realRangeKm: 350,
    summerConsumptionKwhPer100km: null, winterConsumptionKwhPer100km: null,
  }))
  return { stub: { render: () => null }, MODELS: models }
})
vi.mock('../../components/shared/PublicNav.vue', () => ({ default: stub }))
vi.mock('../../components/shared/ThgBanner.vue', () => ({ default: stub }))
vi.mock('../../components/demo/DemoModelsModal.vue', () => ({ default: stub }))
vi.mock('../../components/models/ModelsAnswerCard.vue', () => ({ default: stub }))
vi.mock('../../composables/useModelsListSeo', () => ({ useModelsListSeo: vi.fn() }))
vi.mock('../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
vi.mock('../../api/personalRangeService', () => ({ personalRangeService: { ranking: vi.fn() } }))

vi.mock('../../api/publicModelService', () => ({
  getTopModels: vi.fn(async () => MODELS),
  getPlatformStats: vi.fn(async () => null),
  getCategories: vi.fn(async () => []),
  getChargingReferencePrices: vi.fn(async () => null),
  getModelsWithoutData: vi.fn(async () => []),
}))

/** Models 0 to 9 make the trip with 1 stop, the rest needs 2 */
const figures = (m: TopModelPreview, i: number): PersonalRangeRankingEntry => ({
  brand: m.brand, model: m.model, summerRangeKm: 400, winterRangeKm: 300, longTripStops: i < 10 ? 1 : 2, longTripTotalMinutes: 400,
  chargesPerWeekSummer: 0.5, chargesPerWeekWinter: 0.67, monthlyCostEur: 25, monthlyKm: 460, zeroStopReachKm: 330, oneStopReachKm: 610, twoStopsReachKm: 890,
})

const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
const JWT = `${b64({ alg: 'HS256' })}.${b64({ sub: 'max', exp: 4102444800 })}.sig`

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; localStorage.clear() })
beforeEach(() => {
  vi.mocked(analytics.track).mockReset()
  vi.mocked(personalRangeService.ranking).mockResolvedValue(MODELS.map(figures))
})

async function mount(opts: { signedIn?: boolean; tripKm?: number | null } = {}) {
  if (opts.signedIn) localStorage.setItem('token', JWT)
  if (opts.tripKm) localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify({ longestTripKm: opts.tripKm }))
  setActivePinia(createPinia())
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: stub }] })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PublicModelsListViewV2)
  app.use(i18n).use(router)
  app.mount(host)
  for (let i = 0; i < 5; i++) { await new Promise(r => setTimeout(r)); await nextTick() }
  return host
}

/** Rows in the DOM; jsdom never ends a TransitionGroup leave, so leaving rows do not count */
const rows = (host: HTMLElement) => [...host.querySelectorAll<HTMLElement>('[data-testid="ranking-row"]')].filter(r => !r.classList.contains('v-leave-active'))
const visible = (host: HTMLElement) => rows(host).filter(r => !r.classList.contains('hidden'))
const button = (host: HTMLElement, text: string) => [...host.querySelectorAll<HTMLElement>('button')].find(b => b.textContent?.trim().startsWith(text))

describe('PublicModelsListViewV2, chapter "Was die Fahrer messen"', () => {
  it('shows 8 rows, keeps all 61 in the DOM, and opens the rest on "Alle 61 Modelle"', async () => {
    const host = await mount()
    expect(rows(host)).toHaveLength(61)
    expect(visible(host)).toHaveLength(8)
    // every model stays linked in the HTML, for search engines
    expect(host.querySelectorAll('[data-testid="ranking-row"] a[href^="/modelle/Brand/M"]')).toHaveLength(61)
    expect(host.querySelector('[data-testid="ranking-count"]')!.textContent).toContain('8 von 61 Modellen mit Fahrerdaten')
    button(host, 'Alle 61 Modelle')!.click()
    await nextTick()
    expect(visible(host)).toHaveLength(61)
    expect(button(host, 'Alle 61 Modelle')).toBeUndefined()
  })

  it('a search shows every hit', async () => {
    const host = await mount()
    const input = host.querySelector<HTMLInputElement>('input[type="search"]')!
    input.value = 'M1'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    expect(rows(host).length).toBeGreaterThan(8)
    expect(rows(host).length).toBeLessThan(61)
    expect(visible(host)).toHaveLength(rows(host).length)
  })

  it('guests get no fit filter', async () => {
    const host = await mount({ tripKm: 600 })
    expect(host.querySelector('[data-testid="fit-filter"]')).toBeNull()
  })

  it('signed in with a trip: "Schafft meine 600 km mit 1 Stopp" narrows to those models, tracked', async () => {
    const host = await mount({ signedIn: true, tripKm: 600 })
    const chip = host.querySelector<HTMLButtonElement>('[data-testid="fit-filter"]')!
    expect(chip.textContent).toContain('Schafft meine 600 km mit 1 Stopp')
    expect(chip.getAttribute('aria-pressed')).toBe('false')
    chip.click()
    await nextTick()
    expect(visible(host)).toHaveLength(10)
    expect(analytics.track).toHaveBeenCalledWith('ranking_fit_filter', { stops: 1 })
    host.querySelector<HTMLButtonElement>('[data-testid="fit-all"]')!.click()
    await nextTick()
    expect(visible(host)).toHaveLength(8)
  })

  it('signed in without a trip: no fit filter', async () => {
    const host = await mount({ signedIn: true })
    expect(host.querySelector('[data-testid="fit-filter"]')).toBeNull()
  })
})

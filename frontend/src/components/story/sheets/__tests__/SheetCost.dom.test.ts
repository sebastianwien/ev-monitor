// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import { analytics } from '../../../../services/analytics'
import SheetCost from '../SheetCost.vue'
import type { MonthCosts } from '../../../../composables/storyEnergy'

let app: App | null = null
afterEach(() => {
  app?.unmount(); app = null; document.body.innerHTML = ''
  try { localStorage.removeItem('story_cost_fixed') } catch { /* egal */ }
  vi.restoreAllMocks()
})

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
const flush = async () => { for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() } }

const MONTHS: MonthCosts[] = [
  { energyEur: 40, fixedEur: 45, incomeEur: 0 },
  { energyEur: 30, fixedEur: 45, incomeEur: 0 },
  { energyEur: 20, fixedEur: 0, incomeEur: 0 },
  { energyEur: 25, fixedEur: 45, incomeEur: 0 },
  { energyEur: 18, fixedEur: 45, incomeEur: 0 },
  { energyEur: 15, fixedEur: 45, incomeEur: 20 },
]

const base = {
  tile: { id: 'cost', value: 15, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [40, 30, 20, 25, 18, 15], sparkVariant: 'line' },
  stats: { energyCostEur: 15, fixedCostEur: 45, fixedIncomeEur: 20, totalCostEur: 40 },
  monthLabels: ['Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt'],
  monthName: 'Oktober',
  runningMonth: false,
  hasFixedCosts: true,
  loadMonthlyCosts: () => Promise.resolve(MONTHS),
}

async function mount(props: Partial<typeof base> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div />' } }] })
  app = createApp(SheetCost, { ...base, ...props })
  app.use(createPinia()).use(i18n).use(router)
  app.mount(host)
  await flush()
  return host
}

const chip = (host: Element, name: string) => [...host.querySelectorAll('button')].find(b => text(b) === name) as HTMLButtonElement

describe('SheetCost', () => {
  it('mit Fixkosten: Chips "Strom" und "Mit Fixkosten", Standard Strom mit einfachen Balken', async () => {
    const host = await mount()
    expect(chip(host, 'Strom').getAttribute('aria-pressed')).toBe('true')
    expect(chip(host, 'Mit Fixkosten').getAttribute('aria-pressed')).toBe('false')
    expect([...host.querySelectorAll('[data-role="value"]')].map(e => text(e))).toEqual(['40 €', '30 €', '20 €', '25 €', '18 €', '15 €'])
    expect([...host.querySelectorAll<HTMLElement>('[data-role="stack"]')].every(b => b.style.height === '0%')).toBe(true)
  })

  it('Umschalten ist eine Grafik: dieselben Balken, Stromfarbe bleibt, Fixkosten wachsen darauf', async () => {
    const host = await mount({ tile: { ...base.tile, sentiment: 'warn' } })
    const bars = [...host.querySelectorAll('[data-role="bar"]')]
    chip(host, 'Mit Fixkosten').click()
    await flush()
    expect([...host.querySelectorAll('[data-role="bar"]')]).toEqual(bars)
    expect(bars[0].className).toContain('bg-amber-600')
    expect(bars[0].className).not.toContain('bg-green')
    expect(host.querySelector<HTMLElement>('[data-role="stack"]')!.style.height).not.toBe('0%')
    expect(bars[0].className).toContain('transition-')
  })

  it('"Mit Fixkosten": gestapelt, Summe über dem Balken, Satz zum Monat, Einnahmen als eigener Satz', async () => {
    const track = vi.spyOn(analytics, 'track').mockImplementation(() => {})
    const host = await mount()
    chip(host, 'Mit Fixkosten').click()
    await flush()
    expect([...host.querySelectorAll('[data-role="value"]')].map(e => text(e))).toEqual(['85 €', '75 €', '20 €', '70 €', '63 €', '60 €'])
    expect(host.querySelectorAll('[data-role="stack"]')).toHaveLength(6)
    expect(host.querySelector('[data-testid="story-cost-split"]')!.closest('[inert]:not([inert="false"])')).toBeNull()
    expect(text(host.querySelector('[data-testid="story-cost-split"]'))).toBe('Oktober: 15 € Strom, 45 € Fixkosten.')
    expect(text(host.querySelector('[data-testid="story-cost-income"]'))).toBe('Dazu Einnahmen: 20 €.')
    expect(track).toHaveBeenCalledWith('story_cost_fixed_on')
  })

  it('die Wahl bleibt nach erneutem Öffnen', async () => {
    let host = await mount()
    chip(host, 'Mit Fixkosten').click()
    await flush()
    app!.unmount(); document.body.innerHTML = ''
    host = await mount()
    expect(chip(host, 'Mit Fixkosten').getAttribute('aria-pressed')).toBe('true')
    expect([...host.querySelectorAll<HTMLElement>('[data-role="stack"]')].some(b => b.style.height !== '0%')).toBe(true)
  })

  it('unten eine Zeile zu allen Kosten des Autos', async () => {
    const host = await mount()
    const link = host.querySelector('[data-testid="story-cost-cars"]')
    expect(text(link)).toBe('Alle Kosten deines Autos')
    expect(link?.getAttribute('href')).toBe('/cars')
  })

  it('ohne erfasste Fixkosten: keine Chips, nur die Zeile zum Eintragen', async () => {
    const host = await mount({ hasFixedCosts: false, stats: { energyCostEur: 15, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur: 15 } })
    expect(chip(host, 'Strom')).toBeUndefined()
    expect(chip(host, 'Mit Fixkosten')).toBeUndefined()
    const link = host.querySelector('[data-testid="story-cost-cars"]')
    expect(text(link)).toBe('Versicherung, Steuer, Wartung bei deinem Auto eintragen')
    expect(link?.getAttribute('href')).toBe('/cars')
  })

  it('Monat ohne Fixkosten, andere Monate mit: keine Chips, Zeile zu allen Kosten', async () => {
    const host = await mount({ stats: { energyCostEur: 15, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur: 15 } })
    expect(chip(host, 'Mit Fixkosten')).toBeUndefined()
    expect(text(host.querySelector('[data-testid="story-cost-cars"]'))).toBe('Alle Kosten deines Autos')
  })

  it('Monat ohne Fixkosten: eine gemerkte Wahl zählt nicht', async () => {
    try { localStorage.setItem('story_cost_fixed', 'true') } catch { /* egal */ }
    const host = await mount({ stats: { energyCostEur: 15, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur: 15 } })
    expect([...host.querySelectorAll<HTMLElement>('[data-role="stack"]')].every(b => b.style.height === '0%')).toBe(true)
  })
})

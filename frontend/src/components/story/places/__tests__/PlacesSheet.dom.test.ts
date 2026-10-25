// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import type { ChargingPlace } from '../../../../composables/storyPlaces'

const get = vi.fn()
vi.mock('../../../../api/axios', () => ({ default: { get: (...a: unknown[]) => get(...a) } }))
const bench = vi.fn()
vi.mock('../../../../api/chargingBenchmarkService', () => ({ chargingBenchmarkService: { get: (...a: unknown[]) => bench(...a) } }))
// Leaflet läuft im E2E-Test, hier nur, ob und mit welchen Punkten die Karte steht.
vi.mock('../PlacesMap.vue', () => ({
  __esModule: true,
  default: {
    props: ['pins', 'lines', 'active'],
    template: '<div data-testid="places-map" :data-pins="pins.length" :data-lines="lines.length" />',
    methods: { currentView: () => null, focus: () => {}, restore: () => {} },
  },
}))

import PlacesSheet from '../PlacesSheet.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

const norm = (s: string | null | undefined) => (s ?? '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim()
const $ = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null
const flush = async () => { for (let i = 0; i < 4; i++) { await new Promise(r => setTimeout(r, 0)); await nextTick() } }

function place(over: Partial<ChargingPlace>): ChargingPlace {
  return {
    geohash: 'u1x3k7q', kind: 'PUBLIC', charges: 2, kwh: 80, costEur: 30, pricePerKwh: 0.375,
    lastChargedAt: '2026-08-28T10:00:00', lastLogId: 'log-1', cpoName: 'Tesla Supercharger Hamburg', chargingType: 'DC', ...over,
  }
}
const PLACES: ChargingPlace[] = [
  place({ geohash: 'sc', costEur: 30, pricePerKwh: 0.49 }),
  place({ geohash: 'home', kind: 'HOME', cpoName: null, costEur: 12, pricePerKwh: 0.3 }),
  place({ geohash: 'ionity', cpoName: 'IONITY', costEur: 55, pricePerKwh: 0.52 }),
  place({ geohash: 'road', kind: 'UNKNOWN', cpoName: null, costEur: 0, pricePerKwh: null }),
]
const LINE = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'
const BENCH = { drivers: 15, lowerQuartilePricePerKwh: 0.35, upperQuartilePricePerKwh: 0.45, cards: [] }

let places: ChargingPlace[] = PLACES
let trips: unknown[] = []
beforeEach(() => {
  places = PLACES
  trips = []
  get.mockReset().mockImplementation((url: string) => Promise.resolve({ data: url === '/logs/places' ? places : trips }))
  bench.mockReset().mockImplementation((cpo: string) => Promise.resolve(cpo === 'IONITY'
    ? { ...BENCH, cpoName: 'IONITY', chargingType: 'DC', medianPricePerKwh: 0.52 }
    : { ...BENCH, cpoName: 'Tesla Supercharger', chargingType: 'DC', medianPricePerKwh: 0.39 }))
})

async function mount(extra: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PlacesSheet, {
    carId: 'car-1', month: { year: 2026, month: 8 }, monthName: 'August', canViewLiveAnalytics: true, upsellTo: '/upgrade', ...extra,
  })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await flush()
}
const names = () => [...document.querySelectorAll('[data-testid="places-name"]')].map(n => norm(n.textContent))

describe('PlacesSheet', () => {
  it('Kopfsatz nennt Orte und teure Orte, Zuhause steht oben, dann nach Euro', async () => {
    await mount()

    expect(norm($('places-lead')?.textContent)).toBe('4 Orte in 12 Monaten, an einem zahlst du mehr als andere.')
    expect(names()).toEqual(['Zuhause', 'IONITY', 'Tesla Supercharger Hamburg', 'Ladeort'])
    expect($('places-map')?.dataset.pins).toBe('4')
    expect(bench).toHaveBeenCalledTimes(2)
  })

  it('Zeitraum Monat fragt den Story-Monat ab und ändert den Kopfsatz', async () => {
    await mount()
    places = PLACES.slice(1, 3).map(p => ({ ...p, pricePerKwh: p.kind === 'HOME' ? 0.3 : 0.52 }))

    $('places-period-month')!.click()
    await flush()

    expect(get).toHaveBeenCalledWith('/logs/places', { params: { carId: 'car-1', from: '2026-08-01', to: '2026-08-31' } })
    expect(norm($('places-lead')?.textContent)).toBe('2 Orte im August.')
  })

  it('unter 3 Orten keine Karte, nur die Liste', async () => {
    places = PLACES.slice(0, 2)
    await mount()

    expect($('places-map')).toBeNull()
    expect(names()).toHaveLength(2)
  })

  it('Fahrten-Schalter zählt Fahrten mit Linie und blendet sie ein', async () => {
    trips = [
      { id: 't1', tracePolyline: LINE },
      { id: 't2', routePolyline: LINE, routeKind: 'MATCHED' },
      { id: 't3', routePolyline: LINE, routeKind: 'SKETCH' },
    ]
    await mount()

    const toggle = document.querySelector('[data-testid="places-trips"] [role="switch"]') as HTMLButtonElement
    expect(norm($('places-trips')?.textContent)).toBe('2 Fahrten zeigen')
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    expect($('places-map')?.dataset.lines).toBe('0')
    toggle.click()
    await flush()
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    expect($('places-map')?.dataset.lines).toBe('2')
  })

  it('ohne Fahrt mit Linie kein Fahrten-Schalter', async () => {
    trips = [{ id: 't1' }]
    await mount()

    expect($('places-trips')).toBeNull()
  })

  it('ohne Analytics: nach dem Einblenden eine gesperrte Zeile für alle Fahrten', async () => {
    trips = [{ id: 'newest', tracePolyline: LINE }, { id: 'old' }, { id: 'older' }]
    await mount({ canViewLiveAnalytics: false })

    expect(norm($('places-trips')?.textContent)).toBe('1 Fahrt zeigen')
    expect($('places-trips-locked')).toBeNull()
    ;(document.querySelector('[data-testid="places-trips"] [role="switch"]') as HTMLButtonElement).click()
    await flush()
    expect(norm($('places-trips-locked')?.textContent)).toContain('Alle Fahrten auf der Karte')
    expect($('places-trips-locked')?.querySelector('a')?.getAttribute('href')).toBe('/upgrade')
  })

  it('Betreiber eintragen meldet die neueste Ladung, das Sheet bleibt offen', async () => {
    places = [...PLACES, place({ geohash: 'nocpo', cpoName: null, lastLogId: 'log-9', costEur: 5 })]
    const onOperator = vi.fn()
    await mount({ onOperator })

    $('places-chip-operator')!.click()
    await flush()

    expect(onOperator).toHaveBeenCalledWith('log-9')
    expect($('places-sheet')).not.toBeNull()
  })
})

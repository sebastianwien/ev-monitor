// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import PlacesTeaser from '../PlacesTeaser.vue'
import { teaserParts, type ChargingPlace, type PlaceRow } from '../../../../composables/storyPlaces'
import type { ChargingBenchmark } from '../../../../api/chargingBenchmarkService'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const norm = (s: string | null | undefined) => (s ?? '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim()
const $ = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null

function place(over: Partial<ChargingPlace>): ChargingPlace {
  return {
    geohash: 'u1x3k7q', kind: 'PUBLIC', charges: 2, kwh: 80, costEur: 30, pricePerKwh: 0.49,
    lastChargedAt: '2026-10-05T10:00:00', lastLogId: 'log-1', cpoName: 'Tesla Supercharger Hamburg, Germany', chargingType: 'DC', ...over,
  }
}
const BENCH: ChargingBenchmark = {
  cpoName: 'Tesla Supercharger', chargingType: 'DC', drivers: 15, medianPricePerKwh: 0.39,
  lowerQuartilePricePerKwh: 0.35, upperQuartilePricePerKwh: 0.45, cards: [],
}
const ROWS: PlaceRow[] = [
  { place: place({ geohash: 'u33df', kind: 'HOME', cpoName: null, costEur: 5, pricePerKwh: 0.3 }), cls: 'home', benchmark: null },
  { place: place({}), cls: 'high', benchmark: BENCH },
]

async function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PlacesTeaser, { monthName: 'Oktober', preview: true, ...props })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
}

describe('PlacesTeaser', () => {
  it('teurer Ort: Satz aus den Daten, darunter die Zahl, Kartenbild mit einem Punkt je Ort', async () => {
    const onOpen = vi.fn()
    await mount({ teaser: teaserParts(ROWS), rows: ROWS, onOpen })

    expect(norm($('places-teaser-sentence')?.textContent)).toBe('An 1 von 2 Orten zahlst du mehr als andere.')
    expect(norm($('places-teaser-detail')?.textContent)).toBe('Tesla Supercharger Hamburg: 49 ct/kWh statt 39 ct/kWh')
    expect(document.querySelectorAll('[data-testid="places-teaser-map"] img').length).toBeGreaterThan(0)
    expect(document.querySelectorAll('[data-testid="places-teaser-pin"]')).toHaveLength(2)
    $('story-map-open')!.click()
    expect(onOpen).toHaveBeenCalled()
  })

  it('ohne Vergleich: Orte und Euro des Monats', async () => {
    const rows: PlaceRow[] = [
      { place: place({ costEur: 12.5 }), cls: 'none', benchmark: null },
      { place: place({ geohash: 'u33df', kind: 'HOME', costEur: 2.5 }), cls: 'home', benchmark: null },
    ]
    await mount({ teaser: teaserParts(rows), rows })

    expect(norm($('places-teaser-sentence')?.textContent)).toBe('2 Ladeorte im Oktober, 15 € für Strom.')
    expect($('places-teaser-detail')).toBeNull()
  })

  it('Handy (ohne Vorschau): nur der Satz, kein Kartenbild, keine Kachel-Anfrage', async () => {
    await mount({ teaser: teaserParts(ROWS), rows: ROWS, preview: false })

    expect($('places-teaser-sentence')).not.toBeNull()
    expect($('places-teaser-map')).toBeNull()
    expect(document.querySelectorAll('img')).toHaveLength(0)
  })

  it('ohne Orte im Monat: die Zeile wie bisher', async () => {
    await mount({ teaser: null, rows: [] })

    expect(norm($('story-map-open')?.textContent)).toContain('Wo du lädst')
    expect(norm($('story-map-open')?.textContent)).toContain('Karte')
    expect($('places-teaser-sentence')).toBeNull()
  })
})

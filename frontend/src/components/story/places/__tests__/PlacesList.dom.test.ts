// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import PlacesList from '../PlacesList.vue'
import type { ChargingPlace } from '../../../../composables/storyPlaces'
import type { ChargingBenchmark } from '../../../../api/chargingBenchmarkService'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const norm = (s: string | null | undefined) => (s ?? '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim()

function place(over: Partial<ChargingPlace> = {}): ChargingPlace {
  return {
    geohash: 'u1x3k7q', kind: 'PUBLIC', charges: 3, kwh: 128, costEur: 42.24, pricePerKwh: 0.33,
    lastChargedAt: '2026-08-28T10:00:00', lastLogId: 'log-1',
    cpoName: 'Tesla Supercharger Hamburg, Germany - Stillhorn', chargingType: 'DC', ...over,
  }
}
const BENCH: ChargingBenchmark = {
  cpoName: 'Tesla Supercharger', chargingType: 'DC', drivers: 15, medianPricePerKwh: 0.39,
  lowerQuartilePricePerKwh: 0.35, upperQuartilePricePerKwh: 0.45, cards: [],
}

async function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PlacesList, props)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
}
const rows = () => [...document.querySelectorAll('[data-testid="places-row"]')]

describe('PlacesList', () => {
  it('benennt Orte nach Betreiber, sonst nach Art', async () => {
    await mount({
      rows: [
        { place: place({ geohash: 'home', kind: 'HOME', cpoName: null }), cls: 'home', benchmark: null },
        { place: place({ geohash: 'sc' }), cls: 'usual', benchmark: BENCH },
        { place: place({ geohash: 'pub', cpoName: null }), cls: 'none', benchmark: null },
        { place: place({ geohash: 'unk', kind: 'UNKNOWN', cpoName: null }), cls: 'none', benchmark: null },
        { place: place({ geohash: 'priv', kind: 'PRIVATE', cpoName: null }), cls: 'none', benchmark: null },
      ],
      active: null,
    })

    expect(rows().map(r => norm(r.querySelector('[data-testid="places-name"]')?.textContent)))
      .toEqual(['Zuhause', 'Tesla Supercharger Hamburg', 'Öffentlich', 'Ladeort', 'Privat'])
  })

  it('Unterzeile mit Ladungen, kWh, Preis und letzter Ladung, rechts die Euro-Summe', async () => {
    await mount({
      rows: [
        { place: place(), cls: 'usual', benchmark: BENCH },
        { place: place({ geohash: 'b', charges: 1, kwh: 22.4, costEur: 9.5, pricePerKwh: null }), cls: 'none', benchmark: null },
        { place: place({ geohash: 'c', costEur: 0 }), cls: 'none', benchmark: null },
      ],
      active: null,
    })

    const [a, b, c] = rows()
    expect(norm(a.querySelector('[data-testid="places-sub"]')?.textContent)).toBe('3 Ladungen · 128 kWh · 33 ct/kWh · zuletzt 28.8.')
    expect(norm(a.querySelector('[data-testid="places-eur"]')?.textContent)).toBe('42 €')
    expect(norm(b.querySelector('[data-testid="places-sub"]')?.textContent)).toBe('1 Ladung · 22 kWh · ohne Preis · zuletzt 28.8.')
    expect(norm(b.querySelector('[data-testid="places-eur"]')?.textContent)).toBe('9,50 €')
    expect(norm(c.querySelector('[data-testid="places-eur"]')?.textContent)).toBe('')
  })

  it('teurer Ort: Amber-Chip mit dem Preis der anderen, öffnet den Vergleich', async () => {
    const onPrices = vi.fn()
    await mount({ rows: [{ place: place({ pricePerKwh: 0.49 }), cls: 'high', benchmark: BENCH }], active: null, onPrices })

    const chip = document.querySelector('[data-testid="places-chip-high"]') as HTMLButtonElement
    expect(norm(chip.textContent)).toBe('andere zahlen 39 ct/kWh, 15 Fahrer')
    chip.click()
    expect(onPrices).toHaveBeenCalledWith('u1x3k7q')
  })

  it('günstiger Ort: grüner Chip ohne Handlung', async () => {
    await mount({ rows: [{ place: place({ pricePerKwh: 0.3 }), cls: 'low', benchmark: BENCH }], active: null })

    const chip = document.querySelector('[data-testid="places-chip-low"]')!
    expect(norm(chip.textContent)).toBe('günstiger als 15 andere')
    expect(chip.tagName).toBe('SPAN')
  })

  it('öffentlicher Ort ohne Betreiber: Chip zum Eintragen mit der neuesten Ladung', async () => {
    const onOperator = vi.fn()
    await mount({ rows: [{ place: place({ cpoName: null, lastLogId: 'log-9' }), cls: 'none', benchmark: null }], active: null, onOperator })

    const chip = document.querySelector('[data-testid="places-chip-operator"]') as HTMLButtonElement
    expect(norm(chip.textContent)).toBe('Betreiber eintragen')
    chip.click()
    expect(onOperator).toHaveBeenCalledWith('log-9')
  })

  it('Zeile antippen meldet den Ort, die markierte Zeile ist gedrückt', async () => {
    const onTap = vi.fn()
    await mount({
      rows: [
        { place: place({ geohash: 'a' }), cls: 'usual', benchmark: BENCH },
        { place: place({ geohash: 'b' }), cls: 'usual', benchmark: BENCH },
      ],
      active: 'b',
      onTap,
    })

    const buttons = [...document.querySelectorAll('[data-testid="places-row-button"]')] as HTMLButtonElement[]
    expect(buttons.map(b => b.getAttribute('aria-pressed'))).toEqual(['false', 'true'])
    buttons[0].click()
    expect(onTap).toHaveBeenCalledWith('a')
  })
})

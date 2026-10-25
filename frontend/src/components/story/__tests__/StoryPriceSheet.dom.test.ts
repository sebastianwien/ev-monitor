// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryPriceSheet from '../StoryPriceSheet.vue'
import type { ChargingBenchmark } from '../../../api/chargingBenchmarkService'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const text = () => document.body.textContent!.replace(/ /g, ' ').replace(/\s+/g, ' ')

const BENCH: ChargingBenchmark = {
  cpoName: 'IONITY', chargingType: 'DC', drivers: 18,
  medianPricePerKwh: 0.52, lowerQuartilePricePerKwh: 0.39, upperQuartilePricePerKwh: 0.6,
  cards: [
    { name: 'IONITY', variant: 'Power', pricePerKwh: 0.39, monthlyFeeEur: 11.99, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' },
    { name: 'IONITY Passport', variant: null, pricePerKwh: 0.488, monthlyFeeEur: null, source: 'DRIVERS', drivers: 3, verifiedOn: null },
    { name: 'Maingau', variant: 'Standard', pricePerKwh: 0.62, monthlyFeeEur: 0, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' },
  ],
}

/** BottomSheet blendet sich nach dem Mount ein, der Inhalt steht einen Tick spaeter. */
async function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryPriceSheet, props)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
}

describe('StoryPriceSheet', () => {
  it('nennt den eigenen Preis, was andere Fahrer zahlen, und die Datenbasis', async () => {
    await mount({ benchmark: BENCH, pricePerKwh: 0.79 })
    expect(text()).toContain('Preise bei IONITY')
    expect(text()).toContain('Du hast 79,0 ct/kWh gezahlt.')
    expect(text()).toContain('Andere Fahrer zahlen dort im Schnitt 52,0 ct/kWh, die Hälfte zwischen 39,0 ct/kWh und 60,0 ct/kWh.')
    expect(text()).toContain('18 Fahrer, Schnellladen (DC), letzte 12 Monate')
  })

  it('listet Karten mit Preis, Grundgebühr und Quelle', async () => {
    await mount({ benchmark: BENCH, pricePerKwh: 0.79 })
    const rows = [...document.querySelectorAll('[data-testid="price-card"]')].map(r => r.textContent!.replace(/ /g, ' ').replace(/\s+/g, ' '))
    expect(rows).toHaveLength(3)
    expect(rows[0]).toContain('IONITY Power')
    expect(rows[0]).toContain('39,0 ct/kWh')
    expect(rows[0]).toContain('11,99 € im Monat')
    expect(rows[0]).toContain('Stand 25.03.2026')
    expect(rows[1]).toContain('IONITY Passport')
    expect(rows[1]).toContain('von 3 Fahrern hier bezahlt')
    expect(rows[2]).toContain('ohne Grundgebühr')
  })

  it('ohne Karten keine Kartenliste', async () => {
    await mount({ benchmark: { ...BENCH, cards: [] }, pricePerKwh: null })
    expect(text()).not.toContain('Ladekarten')
    expect(text()).not.toContain('Du hast')
  })
})

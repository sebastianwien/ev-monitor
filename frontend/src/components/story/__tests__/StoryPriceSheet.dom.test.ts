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
  cpoName: 'IONITY', chargingType: 'DC',
  cards: [
    { name: 'IONITY', variant: 'Power', pricePerKwh: 0.39, monthlyFeeEur: 11.99, verifiedOn: '2026-03-25', direct: true },
    { name: 'Maingau', variant: 'Standard', pricePerKwh: 0.62, monthlyFeeEur: 0, verifiedOn: '2026-03-25', direct: false },
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
  it('nennt den eigenen Preis, nie was andere Fahrer zahlen', async () => {
    await mount({ benchmark: BENCH, pricePerKwh: 0.79 })
    expect(text()).toContain('Preise bei IONITY')
    expect(text()).toContain('Du hast 79,0 ct/kWh gezahlt.')
    expect(text()).not.toContain('Fahrer')
    expect(text()).not.toContain('schon wenig')
  })

  it('listet gepflegte Tarife mit Preis, Grundgebühr und Stand; der Betreiber selbst heißt "direkt"', async () => {
    await mount({ benchmark: BENCH, pricePerKwh: 0.79 })
    expect(text()).toContain('Tarife bei IONITY, Schnellladen (DC)')
    const rows = [...document.querySelectorAll('[data-testid="price-card"]')].map(r => r.textContent!.replace(/ /g, ' ').replace(/\s+/g, ' '))
    expect(rows).toHaveLength(2)
    expect(rows[0]).toContain('IONITY direkt')
    expect(rows[0]).toContain('39,0 ct/kWh')
    expect(rows[0]).toContain('ohne Ladekarte')
    expect(rows[0]).toContain('Stand 25.03.2026')
    expect(rows[1]).toContain('Maingau Standard')
    expect(rows[1]).toContain('ohne Grundgebühr')
  })

  it('zahlst du höchstens den günstigsten Tarif, sagt ein Satz das vorab', async () => {
    await mount({ benchmark: BENCH, pricePerKwh: 0.39 })
    expect(document.querySelector('[data-testid="price-cheap"]')!.textContent)
      .toBe('Du zahlst hier schon wenig. Tarife bei IONITY kosten 39 ct/kWh bis 62 ct/kWh.')
  })

  it('ohne Karten keine Kartenliste', async () => {
    await mount({ benchmark: { ...BENCH, cards: [] }, pricePerKwh: null })
    expect(text()).not.toContain('Tarife bei')
    expect(text()).not.toContain('Du hast')
  })
})

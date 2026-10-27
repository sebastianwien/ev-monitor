// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import type { ChargingBenchmark, CardSavings } from '../../../api/chargingBenchmarkService'

const savings = vi.fn()
vi.mock('../../../api/chargingBenchmarkService', () => ({
  chargingBenchmarkService: { savings: (id: string) => savings(id) },
}))

import StoryCardSavings from '../StoryCardSavings.vue'
import StoryPriceDetail from '../StoryPriceDetail.vue'
import StoryPriceSheet from '../StoryPriceSheet.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; savings.mockReset() })

const text = (el: Element | null = document.body) => (el?.textContent ?? '').replace(/ /g, ' ').replace(/ /g, ' ').replace(/\s+/g, ' ').trim()
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }

const SAVINGS: CardSavings = {
  logId: 'log-1', cpoName: 'IONITY', chargingType: 'DC', paidPerKwh: 0.89, kwh: 40,
  offers: [
    { name: 'Maingau', variant: 'Standard', pricePerKwh: 0.62, monthlyFeeEur: 0, verifiedOn: '2026-10-27', chargeSavingsEur: 10.8, feeShareEur: 0, monthSavingsEur: 10.8 },
    { name: 'IONITY', variant: 'Power', pricePerKwh: 0.42, monthlyFeeEur: 11.99, verifiedOn: '2026-10-20', chargeSavingsEur: 6.99, feeShareEur: 11.99, monthSavingsEur: 6.81 },
  ],
}
const BENCH: ChargingBenchmark = {
  cpoName: 'IONITY', chargingType: 'DC', drivers: 18,
  medianPricePerKwh: 0.52, lowerQuartilePricePerKwh: 0.39, upperQuartilePricePerKwh: 0.6,
  cards: [
    { name: 'IONITY', variant: 'Power', pricePerKwh: 0.42, monthlyFeeEur: 11.99, source: 'CURATED', drivers: null, verifiedOn: '2026-10-20' },
    { name: 'IONITY Passport', variant: null, pricePerKwh: 0.488, monthlyFeeEur: null, source: 'DRIVERS', drivers: 3, verifiedOn: null },
  ],
}

function mount(component: unknown, props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(component as never, props)
  app.use(createPinia()).use(i18n)
  app.mount(host)
}

describe('StoryCardSavings', () => {
  it('listet Karten nach Ersparnis für diese Ladung, mit Grundgebühr und Prüfdatum', async () => {
    mount(StoryCardSavings, { savings: SAVINGS })
    await flush()

    expect(text(document.querySelector('h4'))).toBe('Mit Ladekarte an IONITY-Säulen')
    const rows = [...document.querySelectorAll('[data-testid="card-saving"]')].map(r => text(r))
    expect(rows).toHaveLength(2)
    expect(rows[0]).toContain('62 ct')
    expect(rows[0]).toContain('Maingau Standard')
    expect(rows[0]).toContain('ohne Grundgebühr')
    expect(rows[0]).toMatch(/-10 €$/)
    expect(rows[1]).toContain('11,99 € im Monat')
    expect(rows[1]).toMatch(/-6 €$/)
    // ältester Stand der gezeigten Karten
    expect(text()).toContain('Ersparnis für diese Ladung, Grundgebühr anteilig eingerechnet. Preise vom 20.10.2026.')
  })

  it('ohne Angebote: nichts', async () => {
    mount(StoryCardSavings, { savings: { ...SAVINGS, offers: [] } })
    await flush()
    expect(document.querySelector('[data-testid="card-savings"]')).toBeNull()
  })
})

describe('StoryPriceDetail mit Ersparnis', () => {
  it('zeigt die Ersparnis statt der gepflegten Karten, Fahrer-Karten bleiben gekennzeichnet', async () => {
    mount(StoryPriceDetail, { benchmark: BENCH, pricePerKwh: 0.89, savings: SAVINGS })
    await flush()

    expect(document.querySelectorAll('[data-testid="card-saving"]')).toHaveLength(2)
    const listed = [...document.querySelectorAll('[data-testid="price-card"]')].map(r => text(r))
    expect(listed).toHaveLength(1)
    expect(listed[0]).toContain('IONITY Passport')
    expect(listed[0]).toContain('von 3 Fahrern hier bezahlt')
  })

  it('ohne Ersparnis: Kartenliste wie bisher', async () => {
    mount(StoryPriceDetail, { benchmark: BENCH, pricePerKwh: 0.89, savings: null })
    await flush()

    expect(document.querySelector('[data-testid="card-savings"]')).toBeNull()
    expect(document.querySelectorAll('[data-testid="price-card"]')).toHaveLength(2)
  })
})

describe('StoryPriceSheet mit Ladung', () => {
  it('holt die Ersparnis für die Ladung selbst', async () => {
    savings.mockResolvedValue(SAVINGS)
    mount(StoryPriceSheet, { benchmark: BENCH, pricePerKwh: 0.89, logId: 'log-1' })
    await flush()

    expect(savings).toHaveBeenCalledWith('log-1')
    expect(document.querySelectorAll('[data-testid="card-saving"]')).toHaveLength(2)
  })

  it('ohne Ladung oder bei Fehler: kein Abschnitt, Sheet bleibt nutzbar', async () => {
    savings.mockRejectedValue(new Error('offline'))
    mount(StoryPriceSheet, { benchmark: BENCH, pricePerKwh: 0.89, logId: 'log-1' })
    await flush()

    expect(document.querySelector('[data-testid="card-savings"]')).toBeNull()
    expect(text()).toContain('Preise bei IONITY')
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { CrawlOverview, CrawlProposal, CrawlSource } from '../../../api/adminChargingTariffService'

const overview = vi.fn()
const run = vi.fn()
const apply = vi.fn()
const reject = vi.fn()
const createSource = vi.fn()
const updateSource = vi.fn()
const driverCheck = vi.fn()
vi.mock('../../../api/adminChargingTariffService', () => ({
  adminTariffCrawlService: {
    overview: () => overview(),
    run: () => run(),
    apply: (id: string) => apply(id),
    reject: (id: string) => reject(id),
    createSource: (c: unknown) => createSource(c),
    updateSource: (id: string, c: unknown) => updateSource(id, c),
    driverCheck: () => driverCheck(),
  },
}))

import AdminTariffCrawlPanel from '../AdminTariffCrawlPanel.vue'

const source = (over: Partial<CrawlSource>): CrawlSource => ({
  id: 's1', empName: 'IONITY', url: 'https://www.ionity.eu/de/abonnements', mode: 'EXTRACT', enabled: true,
  lastFetchedAt: '2026-10-27T05:40:00', lastSuccessAt: '2026-10-27T05:40:00', lastStatus: 'CHANGED',
  lastHttpStatus: 200, lastError: null, consecutiveFailures: 0, ...over,
})
const proposal = (over: Partial<CrawlProposal>): CrawlProposal => ({
  id: 'p1', sourceId: 's1', empName: 'IONITY', kind: 'CHANGE', description: 'IONITY: Go DC neu 0,68 €/kWh. Sprung über 40 %',
  proposed: { pricePerKwh: 0.68 }, current: null, evidence: 'go-kwh-price 0.68 €/kWh', holdReason: 'Sprung über 40 %',
  seenCount: 2, firstSeenAt: '2026-10-26T05:40:00', lastSeenAt: '2026-10-27T05:40:00', ...over,
})
const sample = (): CrawlOverview => ({
  sources: [
    source({}),
    source({ id: 's2', empName: 'Mer', url: 'https://de.mer.eco/x', lastStatus: 'HTTP_ERROR', lastHttpStatus: 404, lastError: 'HTTP 404', consecutiveFailures: 3 }),
    source({ id: 's3', empName: 'EnBW', mode: 'WATCH', lastStatus: 'UNCHANGED' }),
  ],
  proposals: [proposal({})],
  extractorAvailable: true,
  enabled: true,
})

let app: App | null = null
const changed = vi.fn()
afterEach(() => {
  app?.unmount(); app = null
  document.body.innerHTML = ''
  for (const m of [overview, run, apply, reject, createSource, updateSource, changed, driverCheck]) m.mockReset()
})

async function mount(data: CrawlOverview = sample(), hints: unknown[] = []): Promise<HTMLElement> {
  overview.mockResolvedValue(data)
  driverCheck.mockResolvedValue(hints)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(AdminTariffCrawlPanel, { onChanged: changed }) })
  app.mount(host)
  await flush()
  return host
}

const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim()
const button = (host: HTMLElement, label: string) =>
  [...host.querySelectorAll('button')].find(b => text(b) === label)!

describe('AdminTariffCrawlPanel', () => {
  it('zeigt Quellen mit letztem Ergebnis, Fehler deutlich', async () => {
    const host = await mount()
    const rows = [...host.querySelectorAll('[data-testid="crawl-source"]')].map(text)
    expect(rows).toHaveLength(3)
    expect(rows[0]).toContain('IONITY')
    expect(rows[0]).toContain('geändert')
    expect(rows[1]).toContain('HTTP 404')
    expect(rows[1]).toContain('3 Läufe in Folge')
    expect(rows[2]).toContain('nur Bilder')
  })

  it('zeigt offene Vorschläge als Satz mit Zitat und übernimmt auf Knopfdruck', async () => {
    const host = await mount()
    const p = host.querySelector('[data-testid="crawl-proposal"]')!
    expect(text(p)).toContain('IONITY: Go DC neu 0,68 €/kWh')
    expect(text(p)).toContain('go-kwh-price 0.68 €/kWh')
    apply.mockResolvedValue(true)
    overview.mockResolvedValue({ ...sample(), proposals: [] })

    button(host, 'Übernehmen').click()
    await flush()

    expect(apply).toHaveBeenCalledWith('p1')
    expect(changed).toHaveBeenCalled()
    expect(host.querySelector('[data-testid="crawl-proposal"]')).toBeNull()
  })

  it('überholter Vorschlag: sagt es, statt still nichts zu tun', async () => {
    const host = await mount()
    apply.mockResolvedValue(false)

    button(host, 'Übernehmen').click()
    await flush()

    expect(text(host)).toContain('Der Tarif wurde inzwischen anders gepflegt')
  })

  it('verwirft einen Vorschlag', async () => {
    const host = await mount()
    reject.mockResolvedValue(undefined)

    button(host, 'Verwerfen').click()
    await flush()

    expect(reject).toHaveBeenCalledWith('p1')
  })

  it('startet einen Lauf und sagt, wenn schon einer läuft', async () => {
    const host = await mount()
    run.mockResolvedValueOnce(true).mockResolvedValueOnce(false)

    button(host, 'Jetzt alle Seiten prüfen').click()
    await flush()
    expect(text(host)).toContain('Prüfung läuft im Hintergrund')

    button(host, 'Jetzt alle Seiten prüfen').click()
    await flush()
    expect(text(host)).toContain('Es läuft schon eine Prüfung')
  })

  it('ohne Sprachmodell: Hinweis, dass nur Bild-Quellen geprüft werden', async () => {
    const host = await mount({ ...sample(), extractorAvailable: false })
    expect(text(host)).toContain('MISTRAL_API_KEY')
  })

  it('zeigt, wo Fahrer deutlich anders zahlen als gepflegt', async () => {
    const host = await mount(sample(), [{ empName: 'IONITY', operator: 'IONITY', chargingType: 'DC', nearestVariant: 'Power',
      curatedPricePerKwh: 0.42, driverMedianPricePerKwh: 0.55, drivers: 5, charges: 12 }])
    const hint = text(host.querySelector('[data-testid="driver-hint"]'))
    expect(hint).toContain('IONITY Power an IONITY DC')
    expect(hint).toContain('gepflegt 0,42 €')
    expect(hint).toContain('Fahrer zahlen 0,55 €')
    expect(hint).toContain('5 Fahrer, 12 Ladungen')
  })

  it('schaltet eine Quelle ab', async () => {
    const host = await mount()
    updateSource.mockResolvedValue(source({ enabled: false }))

    ;(host.querySelector('[data-testid="crawl-source"] input[type="checkbox"]') as HTMLInputElement).click()
    await flush()

    expect(updateSource).toHaveBeenCalledWith('s1', { empName: 'IONITY', url: 'https://www.ionity.eu/de/abonnements', mode: 'EXTRACT', enabled: false })
  })
})

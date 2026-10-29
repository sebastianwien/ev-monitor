// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { CrawlOverview, CrawlProposal, CrawlSource } from '../../../api/adminChargingTariffService'

const run = vi.fn()
const createSource = vi.fn()
vi.mock('../../../api/adminChargingTariffService', () => ({
  adminTariffCrawlService: {
    run: () => run(),
    createSource: (c: unknown) => createSource(c),
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
  proposed: { pricePerKwh: 0.68 }, current: null, evidence: 'go-kwh-price 0.68 €/kWh', evidenceBefore: null, evidenceAfter: null, holdReason: 'Sprung über 40 %',
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
  autoApply: false,
})

let app: App | null = null
const changed = vi.fn()
afterEach(() => {
  app?.unmount(); app = null
  document.body.innerHTML = ''
  for (const m of [run, createSource, changed]) m.mockReset()
})

/** Kopf des Crawlers: Lauf starten, Hinweise, Quelle anlegen. Quellen und Vorschläge zeigt der Baum je Anbieter. */
async function mount(data: CrawlOverview | null = sample()): Promise<HTMLElement> {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(AdminTariffCrawlPanel, { overview: data, onChanged: changed }) })
  app.mount(host)
  await flush()
  return host
}

const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim()
const button = (host: HTMLElement, label: string) =>
  [...host.querySelectorAll('button')].find(b => text(b) === label)!

describe('AdminTariffCrawlPanel', () => {
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
    expect(text(host)).toContain('ANTHROPIC_API_KEY oder MISTRAL_API_KEY')
  })

  it('ohne automatische Übernahme: jede Preisänderung wartet auf die Entscheidung', async () => {
    const manual = await mount(sample())
    expect(text(manual)).toContain('Jede Preisänderung steht beim Anbieter mit Zitat')
    expect(text(manual)).not.toContain('übernimmt er nach einer zweiten Bestätigung selbst')
    app?.unmount()
    app = null
    const auto = await mount({ ...sample(), autoApply: true })
    expect(text(auto)).toContain('übernimmt er nach einer zweiten Bestätigung selbst')
  })

  it('legt eine Quelle an und meldet die Änderung', async () => {
    createSource.mockResolvedValue(source({ id: 's9' }))
    const host = await mount()
    button(host, 'Quelle hinzufügen').click()
    await flush()
    const set = (name: string, value: string) => { const el = host.querySelector(`[name="${name}"]`) as HTMLInputElement; el.value = value; el.dispatchEvent(new Event('input')) }
    set('crawlEmp', 'EWE Go')
    set('crawlUrl', 'https://www.ewe-go.de/tarife')
    button(host, 'Anlegen').click()
    await flush()

    expect(createSource).toHaveBeenCalledWith({ empName: 'EWE Go', url: 'https://www.ewe-go.de/tarife', mode: 'EXTRACT', enabled: true })
    expect(changed).toHaveBeenCalled()
  })
})

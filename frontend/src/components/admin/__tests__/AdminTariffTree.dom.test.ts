// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { CrawlProposal, CrawlSource, DriverDeviation, TariffRow } from '../../../api/adminChargingTariffService'
import AdminTariffTree from '../AdminTariffTree.vue'

const row = (over: Partial<TariffRow>): TariffRow => ({
  id: 't1', empName: 'IONITY', tariffVariant: 'Power', cpoName: null, priceTier: null, chargingType: 'DC',
  pricePerKwh: 0.42, sessionFeeEur: 0, monthlyFeeEur: 11.99, blockingFeePerMin: null, blockingFeeAfterMin: null,
  dynamicPricing: false, validFrom: '2026-10-27', validUntil: null, sourceUrl: 'https://www.ionity.eu/de/abonnements',
  lastVerifiedAt: '2026-10-29T12:55:00', appliesElsewhere: false, minPowerKw: null, blockingFeeCapEur: null, ...over,
})
const source = (over: Partial<CrawlSource>): CrawlSource => ({
  id: 's1', empName: 'IONITY', url: 'https://www.ionity.eu/de/abonnements', mode: 'EXTRACT', enabled: true,
  lastFetchedAt: '2026-10-29T12:55:00', lastSuccessAt: '2026-10-29T12:55:00', lastStatus: 'OK',
  lastHttpStatus: 200, lastError: null, consecutiveFailures: 0, ...over,
})
const proposal = (over: Partial<CrawlProposal>): CrawlProposal => ({
  id: 'p1', sourceId: 's2', empName: 'Smatrics', kind: 'CHANGE', description: 'Smatrics: Charge Simple AC neu 0,55 €/kWh',
  proposed: { variant: 'Charge Simple', chargingType: 'AC', scope: 'OWN_NETWORK', pricePerKwh: 0.55, monthlyFeeEur: 0,
    blockingFeePerMin: 0.1, blockingFeeAfterMin: 481, blockingFeeCapEur: 18 },
  current: null, evidence: '0,55€', evidenceBefore: 'Charge Simple · AC · ', evidenceAfter: ' pro kWh · Blockiergebühr',
  holdReason: null, seenCount: 1, firstSeenAt: '2026-10-29T12:55:00', lastSeenAt: '2026-10-29T12:55:00', ...over,
})

const simpleAc = row({ id: 't2', empName: 'Smatrics', tariffVariant: 'Charge Simple', chargingType: 'AC', pricePerKwh: 0.55,
  monthlyFeeEur: 0, blockingFeePerMin: 0.1, blockingFeeAfterMin: 481, sourceUrl: 'https://www.smatrics.com/de/laden/tarife' })
const roamPlus = row({ id: 't3', empName: 'Smatrics', tariffVariant: 'Charge & Roam+', chargingType: 'DC', pricePerKwh: 0.55, monthlyFeeEur: 8.9 })
const props = () => ({
  tariffs: [row({}), simpleAc, roamPlus, row({ id: 't4', empName: 'ADAC e-Charge', tariffVariant: 'Standard', pricePerKwh: 0.75, monthlyFeeEur: 0, appliesElsewhere: true })],
  cpos: ['IONITY', 'Smatrics'],
  proposals: [
    proposal({ current: simpleAc }),
    proposal({ id: 'p2', kind: 'MISSING', description: 'Smatrics: Charge & Roam+ DC steht nicht mehr auf der Seite', current: roamPlus,
      proposed: { variant: 'Charge & Roam+' }, evidence: null, evidenceBefore: null, evidenceAfter: null, holdReason: 'Steht nicht mehr auf der Seite' }),
    proposal({ id: 'p3', kind: 'NEW', description: 'Smatrics: neuer Tarif Direct Payment AC 0,49 €/kWh', current: null,
      proposed: { variant: 'Direct Payment', chargingType: 'AC', scope: 'OWN_NETWORK', pricePerKwh: 0.49, monthlyFeeEur: null },
      evidence: 'AC-Ladepunkt 0,49 € / kWh', evidenceBefore: 'Direct Payment · ', evidenceAfter: '', holdReason: 'Neuer Tarif auf der Seite' }),
  ],
  sources: [source({}), source({ id: 's2', empName: 'Smatrics', url: 'https://www.smatrics.com/de/laden/tarife', lastStatus: 'CHANGED' }),
    source({ id: 's3', empName: 'ADAC e-Charge', url: 'https://www.adac.de/x', lastStatus: 'HTTP_ERROR', lastError: 'HTTP 404', consecutiveFailures: 2 })],
  hints: [] as DriverDeviation[],
  busyId: null as string | null,
})

let app: App | null = null
const events = { verify: vi.fn(), edit: vi.fn(), end: vi.fn(), decide: vi.fn(), toggleSource: vi.fn() }
afterEach(() => {
  app?.unmount(); app = null
  document.body.innerHTML = ''
  Object.values(events).forEach(e => e.mockReset())
})

async function mount(p = props()): Promise<HTMLElement> {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(AdminTariffTree, { ...p, onVerify: events.verify, onEdit: events.edit, onEnd: events.end,
    onDecide: events.decide, onToggleSource: events.toggleSource }) })
  app.mount(host)
  await flush()
  return host
}
const flush = async () => { for (let i = 0; i < 4; i++) await nextTick() }
const text = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim()
const nodes = (host: HTMLElement) => [...host.querySelectorAll('[data-testid="tariff-node"]')] as HTMLElement[]
const nameOf = (n: Element) => text(n.querySelector('[data-testid="node-name"]'))
const node = (host: HTMLElement, name: string) => nodes(host).find(n => nameOf(n).startsWith(name))!
const button = (el: Element, label: string) => [...el.querySelectorAll('button')].find(b => text(b) === label)!

describe('AdminTariffTree', () => {
  it('ein Knoten je Anbieter, offene Vorschläge zuerst und aufgeklappt, der Rest zu', async () => {
    const host = await mount()
    expect(nodes(host).map(nameOf)).toEqual(['Smatrics', 'ADAC e-Charge', 'IONITY'])
    expect(text(node(host, 'Smatrics').querySelector('[data-testid="node-toggle"]'))).toContain('2 Tarife')
    expect(text(node(host, 'Smatrics').querySelector('[data-testid="node-toggle"]'))).toContain('1 Änderung · 1 neu · 1 fehlt')
    expect(node(host, 'Smatrics').querySelectorAll('[data-testid="tariff-row"]')).toHaveLength(2)
    expect(text(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]'))).toContain('nichts offen')
    expect(node(host, 'IONITY').querySelectorAll('[data-testid="tariff-row"]')).toHaveLength(0)

    ;(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]') as HTMLElement).click()
    await flush()
    expect(node(host, 'IONITY').querySelectorAll('[data-testid="tariff-row"]')).toHaveLength(1)
  })

  it('zeigt im Kopf, ob die Seite lesbar war, Fehler deutlich', async () => {
    const host = await mount()
    expect(text(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]'))).toContain('gelesen 29.10.2026')
    expect(text(node(host, 'ADAC').querySelector('[data-testid="node-toggle"]'))).toContain('nicht erreichbar: HTTP 404, 2 Läufe in Folge')
  })

  it('Änderung hängt an ihrer Tarifzeile und nennt nur, was anders ist, mit Satz von der Seite', async () => {
    const host = await mount()
    const p = node(host, 'Smatrics').querySelector('[data-testid="crawl-proposal"][data-kind="CHANGE"]')!
    expect(p.previousElementSibling?.getAttribute('data-testid')).toBe('tariff-row')
    expect(text(p.previousElementSibling)).toContain('Charge Simple')
    expect(text(p)).toContain('Blockiergebühr höchstens: 18,00 € statt -')
    expect(text(p)).not.toContain('Preis:')
    expect(text(p)).toContain('„Charge Simple · AC · 0,55€ pro kWh · Blockiergebühr“')
    expect(p.querySelector('mark')?.textContent).toBe('0,55€')
    expect(text(p)).toContain('bisherige Zeile endet gestern, neue Werte gelten ab heute')
  })

  it('springt zur Stelle auf der Seite (Textfragment)', async () => {
    const host = await mount()
    const link = node(host, 'Smatrics').querySelector('[data-testid="crawl-proposal"][data-kind="NEW"] a[data-testid="evidence-link"]') as HTMLAnchorElement
    expect(link.href).toBe('https://www.smatrics.com/de/laden/tarife#:~:text=AC%2DLadepunkt%200%2C49%20%E2%82%AC%20%2F%20kWh')
    expect(link.target).toBe('_blank')
  })

  it('mehrzeiliges Zitat: Textfragment mit Anfang und Ende', async () => {
    const p = props()
    p.proposals[2] = { ...p.proposals[2], evidence: 'Direct Payment · AC-Ladepunkt 0,49 € / kWh' }
    const host = await mount(p)
    const link = node(host, 'Smatrics').querySelector('[data-kind="NEW"] a[data-testid="evidence-link"]') as HTMLAnchorElement
    expect(link.href).toBe('https://www.smatrics.com/de/laden/tarife#:~:text=Direct%20Payment,AC%2DLadepunkt%200%2C49%20%E2%82%AC%20%2F%20kWh')
  })

  it('alter Eintrag ohne Ausschnitt: nur das Zitat, kein "null"', async () => {
    const p = props()
    p.proposals[0] = { ...p.proposals[0], evidenceBefore: null, evidenceAfter: null }
    const host = await mount(p)
    const change = node(host, 'Smatrics').querySelector('[data-kind="CHANGE"]')!
    expect(text(change)).toContain('Seite: „0,55€“')
    expect(text(change)).not.toContain('null')
  })

  it('nie gelesene Seite heißt so', async () => {
    const p = props()
    p.sources[0] = { ...p.sources[0], lastFetchedAt: null, lastSuccessAt: null, lastStatus: null }
    const host = await mount(p)
    expect(text(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]'))).toContain('noch nicht gelesen')
  })

  it('fehlender Tarif: sagt, dass Übernehmen ihn beendet', async () => {
    const host = await mount()
    const p = node(host, 'Smatrics').querySelector('[data-testid="crawl-proposal"][data-kind="MISSING"]')!
    expect(text(p.previousElementSibling)).toContain('Charge & Roam+')
    expect(text(p)).toContain('Steht laut letztem Lauf nicht mehr auf der Seite')
    expect(text(p)).toContain('Übernehmen beendet diesen Tarif')
  })

  it('neuer Tarif steht unter "Neu auf der Seite" wie eine Tarifzeile', async () => {
    const host = await mount()
    const p = node(host, 'Smatrics').querySelector('[data-testid="crawl-proposal"][data-kind="NEW"]')!
    expect(text(p)).toContain('Direct Payment · eigenes Netz · AC · 49,0 ct')
    expect(text(p)).toContain('Übernehmen legt den Tarif ab heute an')
    expect(text(node(host, 'Smatrics'))).toContain('Neu auf der Seite')
  })

  it('Knöpfe melden die Entscheidung und die Tarifaktionen nach oben', async () => {
    const host = await mount()
    const p = node(host, 'Smatrics').querySelector('[data-testid="crawl-proposal"][data-kind="CHANGE"]')!
    button(p, 'Übernehmen').click()
    button(p, 'Verwerfen').click()
    ;(node(host, 'Smatrics').querySelector('[data-testid="tariff-row"] button[data-action="verify"]') as HTMLElement).click()
    ;(node(host, 'Smatrics').querySelector('[data-testid="crawl-source"] input[type="checkbox"]') as HTMLInputElement).click()
    await flush()

    expect(events.decide.mock.calls.map(c => [c[0].id, c[1]])).toEqual([['p1', 'apply'], ['p1', 'reject']])
    expect(events.verify).toHaveBeenCalledWith(simpleAc)
    expect(events.toggleSource.mock.calls[0][0].id).toBe('s2')
  })

  it('alle auf- und zuklappen', async () => {
    const host = await mount()
    button(host, 'Alle aufklappen').click()
    await flush()
    expect(host.querySelectorAll('[data-testid="tariff-row"]')).toHaveLength(4)
    button(host, 'Alle zuklappen').click()
    await flush()
    expect(host.querySelectorAll('[data-testid="tariff-row"]')).toHaveLength(0)
  })

  it('zeigt beim Anbieter, wo Fahrer deutlich anders zahlen', async () => {
    const host = await mount({ ...props(), hints: [{ empName: 'IONITY', operator: 'IONITY', chargingType: 'DC', nearestVariant: 'Power',
      curatedPricePerKwh: 0.42, driverMedianPricePerKwh: 0.55, drivers: 5, charges: 12 }] })
    expect(text(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]'))).toContain('1 Hinweis')
    ;(node(host, 'IONITY').querySelector('[data-testid="node-toggle"]') as HTMLElement).click()
    await flush()
    const hint = text(node(host, 'IONITY').querySelector('[data-testid="driver-hint"]'))
    expect(hint).toContain('Power an IONITY DC: gepflegt 0,42 €, Fahrer zahlen 0,55 € (5 Fahrer, 12 Ladungen)')
  })
})

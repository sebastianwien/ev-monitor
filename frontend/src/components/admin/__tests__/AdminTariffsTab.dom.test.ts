// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { CrawlOverview, CrawlProposal, TariffOverview, TariffRow } from '../../../api/adminChargingTariffService'

const overview = vi.fn()
const create = vi.fn()
const update = vi.fn()
const end = vi.fn()
const verify = vi.fn()
const crawlOverview = vi.fn()
const apply = vi.fn()
const reject = vi.fn()
const updateSource = vi.fn()
vi.mock('../../../api/adminChargingTariffService', () => ({
  adminChargingTariffService: {
    overview: () => overview(),
    create: (c: unknown) => create(c),
    update: (id: string, c: unknown) => update(id, c),
    end: (id: string, until: string) => end(id, until),
    verify: (id: string) => verify(id),
  },
  adminTariffCrawlService: {
    overview: () => crawlOverview(),
    driverCheck: () => Promise.resolve([]),
    apply: (id: string) => apply(id),
    reject: (id: string) => reject(id),
    updateSource: (id: string, c: unknown) => updateSource(id, c),
  },
}))

import AdminTariffsTab from '../AdminTariffsTab.vue'

const row = (over: Partial<TariffRow>): TariffRow => ({
  id: 't1', empName: 'IONITY', tariffVariant: 'Power', cpoName: null, priceTier: null, chargingType: 'DC',
  pricePerKwh: 0.39, sessionFeeEur: 0, monthlyFeeEur: 11.99, blockingFeePerMin: null, blockingFeeAfterMin: null,
  dynamicPricing: false, validFrom: '2025-01-01', validUntil: null, sourceUrl: 'https://ionity.eu/de/abonnements',
  lastVerifiedAt: '2026-03-25T10:00:00', appliesElsewhere: false, minPowerKw: null, blockingFeeCapEur: null, ...over,
})
const sample: TariffOverview = {
  tariffs: [
    row({}),
    row({ id: 't2', empName: 'EnBW', tariffVariant: 'Tariff S', pricePerKwh: 0.59, monthlyFeeEur: 0, validUntil: '2025-11-30' }),
    row({ id: 't3', empName: 'Maingau', tariffVariant: 'Standard', priceTier: 'HIGH', pricePerKwh: 0.82, monthlyFeeEur: 0 }),
    row({ id: 't4', empName: 'ADAC e-Charge', tariffVariant: 'Standard', pricePerKwh: 0.55, monthlyFeeEur: 0 }),
    row({ id: 't5', empName: 'ADAC e-Charge', tariffVariant: 'Standard', pricePerKwh: 0.75, monthlyFeeEur: 0, appliesElsewhere: true }),
    row({ id: 't6', empName: 'IONITY', tariffVariant: 'Direct', pricePerKwh: 0.72, monthlyFeeEur: 0, minPowerKw: 50 }),
  ],
  emps: ['EnBW', 'IONITY', 'Maingau'],
  cpos: ['EnBW', 'IONITY'],
}

const change = (): CrawlProposal => ({
  id: 'p1', sourceId: 's1', empName: 'IONITY', kind: 'CHANGE', description: 'IONITY: Power DC neu 0,41 €/kWh',
  proposed: { variant: 'Power', chargingType: 'DC', scope: 'OWN_NETWORK', pricePerKwh: 0.41 }, current: sample.tariffs[0],
  evidence: '0.41 €/kWh', evidenceBefore: '', evidenceAfter: ' power-monthly-kwh-price', holdReason: null,
  seenCount: 1, firstSeenAt: '2026-10-29T12:55:00', lastSeenAt: '2026-10-29T12:55:00',
})
const crawl = (proposals: CrawlProposal[] = []): CrawlOverview => ({
  sources: [{ id: 's1', empName: 'IONITY', url: 'https://www.ionity.eu/de/abonnements', mode: 'EXTRACT', enabled: true,
    lastFetchedAt: '2026-10-29T12:55:00', lastSuccessAt: '2026-10-29T12:55:00', lastStatus: 'CHANGED', lastHttpStatus: 200,
    lastError: null, consecutiveFailures: 0 }],
  proposals, extractorAvailable: true, enabled: true, autoApply: false,
})

let app: App | null = null
afterEach(() => {
  app?.unmount(); app = null
  document.body.innerHTML = ''
  for (const m of [overview, create, update, end, verify, crawlOverview, apply, reject, updateSource]) m.mockReset()
  vi.restoreAllMocks()
})

/** Alle Anbieter aufgeklappt, damit jede Tarifzeile sichtbar ist. */
async function mount(proposals: CrawlProposal[] = []): Promise<HTMLElement> {
  overview.mockResolvedValue(sample)
  crawlOverview.mockResolvedValue(crawl(proposals))
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(AdminTariffsTab)
  app.mount(host)
  await flush()
  button(host, 'Alle aufklappen').click()
  await flush()
  return host
}

const flush = async () => { for (let i = 0; i < 4; i++) await nextTick() }
const button = (host: Element, text: string) =>
  [...host.querySelectorAll('button')].find(b => b.textContent?.replace(/\s+/g, ' ').trim() === text)!
const rows = (host: HTMLElement) => [...host.querySelectorAll('[data-testid="tariff-row"]')].map(r => r.textContent!.replace(/\s+/g, ' '))
const rowWith = (host: HTMLElement, content: string) =>
  [...host.querySelectorAll('[data-testid="tariff-row"]')].find(r => r.textContent!.replace(/\s+/g, ' ').includes(content))!
const input = (host: HTMLElement, name: string) => host.querySelector(`[name="${name}"]`) as HTMLInputElement

describe('AdminTariffsTab', () => {
  it('zeigt gültige Tarife je Anbieter mit Geltungsbereich, beendete erst auf Wunsch', async () => {
    const host = await mount()
    expect(rows(host)).toHaveLength(5)
    expect(rowWith(host, '39,0 ct')).toBeTruthy()
    expect(rows(host).find(r => r.includes('39,0 ct'))).toContain('eigenes Netz')
    expect(rows(host).find(r => r.includes('39,0 ct'))).toContain('11,99 €')
    expect(rows(host).find(r => r.includes('82,0 ct'))).toContain('Tier HIGH')
    // ohne eigenes Netz im Katalog gilt der Tarif nirgends
    expect(rows(host).find(r => r.includes('55,0 ct'))).toContain('nirgends')

    ;(host.querySelector('[name="showEnded"]') as HTMLInputElement).click()
    await flush()
    expect(rows(host)).toHaveLength(6)
    expect(rows(host).join(' ')).toContain('bis 30.11.2025')
  })

  it('zeigt Roaming-Preise und Leistungsklassen', async () => {
    const host = await mount()
    const all = rows(host)
    expect(all.find(r => r.includes('75,0 ct'))).toContain('alle anderen Betreiber')
    expect(all.find(r => r.includes('72,0 ct'))).toContain('DC über 50 kW')
  })

  it('markiert einen Tarif als geprüft und lädt neu', async () => {
    verify.mockResolvedValue(row({ lastVerifiedAt: '2026-10-27T12:00:00' }))
    const host = await mount()

    rowWith(host, '39,0 ct').querySelector('button[data-action="verify"]')!.dispatchEvent(new Event('click'))
    await flush()

    expect(verify).toHaveBeenCalledWith('t1')
    expect(overview).toHaveBeenCalledTimes(2)
  })

  it('beendet einen Tarif zum gewählten Tag und lädt neu', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('2026-10-25')
    end.mockResolvedValue(row({ validUntil: '2026-10-25' }))
    const host = await mount()

    rowWith(host, '39,0 ct').querySelector('button[data-action="end"]')!.dispatchEvent(new Event('click'))
    await flush()

    expect(end).toHaveBeenCalledWith('t1', '2026-10-25')
    expect(overview).toHaveBeenCalledTimes(2)
  })

  it('legt einen Tarif an', async () => {
    create.mockResolvedValue(row({ id: 't9' }))
    const host = await mount()
    button(host, 'Neuer Tarif').click()
    await flush()

    const set = (name: string, value: string) => { const el = input(host, name); el.value = value; el.dispatchEvent(new Event('input')); el.dispatchEvent(new Event('change')) }
    set('empName', 'EWE Go')
    set('tariffVariant', 'Standard')
    set('pricePerKwh', '0.52')
    set('validFrom', '2026-10-01')
    set('sourceUrl', 'https://www.ewe-go.de/tarife')
    button(host, 'Speichern').click()
    await flush()

    expect(create).toHaveBeenCalledWith(expect.objectContaining({
      empName: 'EWE Go', tariffVariant: 'Standard', chargingType: 'DC', pricePerKwh: 0.52, validFrom: '2026-10-01',
      sourceUrl: 'https://www.ewe-go.de/tarife', cpoName: null, priceTier: null,
      appliesElsewhere: false, minPowerKw: null, blockingFeeCapEur: null,
    }))
  })

  it('ändert einen Tarif mit vorbelegtem Formular', async () => {
    update.mockResolvedValue(row({ pricePerKwh: 0.41 }))
    const host = await mount()
    rowWith(host, '39,0 ct').querySelector('button[data-action="edit"]')!.dispatchEvent(new Event('click'))
    await flush()

    expect(input(host, 'empName').value).toBe('IONITY')
    const price = input(host, 'pricePerKwh'); price.value = '0.41'; price.dispatchEvent(new Event('input'))
    button(host, 'Speichern').click()
    await flush()

    expect(update).toHaveBeenCalledWith('t1', expect.objectContaining({ empName: 'IONITY', tariffVariant: 'Power', pricePerKwh: 0.41, monthlyFeeEur: 11.99 }))
  })

  it('übernimmt einen Vorschlag und lädt Tarife und Crawler neu', async () => {
    apply.mockResolvedValue(true)
    const host = await mount([change()])
    crawlOverview.mockResolvedValue(crawl([]))

    button(host.querySelector('[data-testid="crawl-proposal"]')!, 'Übernehmen').click()
    await flush()

    expect(apply).toHaveBeenCalledWith('p1')
    expect(overview).toHaveBeenCalledTimes(2)
    expect(host.querySelector('[data-testid="crawl-proposal"]')).toBeNull()
  })

  it('überholter Vorschlag: sagt es, statt still nichts zu tun', async () => {
    apply.mockResolvedValue(false)
    const host = await mount([change()])

    button(host.querySelector('[data-testid="crawl-proposal"]')!, 'Übernehmen').click()
    await flush()

    expect(host.textContent).toContain('Der Tarif wurde inzwischen anders gepflegt')
  })

  it('verwirft einen Vorschlag und schaltet eine Quelle ab', async () => {
    reject.mockResolvedValue(undefined)
    updateSource.mockResolvedValue(undefined)
    const host = await mount([change()])

    button(host.querySelector('[data-testid="crawl-proposal"]')!, 'Verwerfen').click()
    ;(host.querySelector('[data-testid="crawl-source"] input[type="checkbox"]') as HTMLInputElement).click()
    await flush()

    expect(reject).toHaveBeenCalledWith('p1')
    expect(updateSource).toHaveBeenCalledWith('s1', { empName: 'IONITY', url: 'https://www.ionity.eu/de/abonnements', mode: 'EXTRACT', enabled: false })
  })

  it('Crawler nicht erreichbar: sagt es, die Tarife stehen trotzdem da', async () => {
    overview.mockResolvedValue(sample)
    crawlOverview.mockRejectedValue(new Error('500'))
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AdminTariffsTab)
    app.mount(host)
    await flush()

    expect(host.textContent).toContain('Crawler-Daten konnten nicht geladen werden')
    expect(host.querySelectorAll('[data-testid="tariff-node"]').length).toBeGreaterThan(0)
  })
})

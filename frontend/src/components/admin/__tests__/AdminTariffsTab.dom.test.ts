// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { TariffOverview, TariffRow } from '../../../api/adminChargingTariffService'

const overview = vi.fn()
const create = vi.fn()
const update = vi.fn()
const end = vi.fn()
vi.mock('../../../api/adminChargingTariffService', () => ({
  adminChargingTariffService: {
    overview: () => overview(),
    create: (c: unknown) => create(c),
    update: (id: string, c: unknown) => update(id, c),
    end: (id: string, until: string) => end(id, until),
  },
}))

import AdminTariffsTab from '../AdminTariffsTab.vue'

const row = (over: Partial<TariffRow>): TariffRow => ({
  id: 't1', empName: 'IONITY', tariffVariant: 'Power', cpoName: null, priceTier: null, chargingType: 'DC',
  pricePerKwh: 0.39, sessionFeeEur: 0, monthlyFeeEur: 11.99, blockingFeePerMin: null, blockingFeeAfterMin: null,
  dynamicPricing: false, validFrom: '2025-01-01', validUntil: null, sourceUrl: 'https://ionity.eu/de/abonnements',
  lastVerifiedAt: '2026-03-25T10:00:00', ...over,
})
const sample: TariffOverview = {
  tariffs: [
    row({}),
    row({ id: 't2', empName: 'EnBW', tariffVariant: 'Tariff S', pricePerKwh: 0.59, monthlyFeeEur: 0, validUntil: '2025-11-30' }),
    row({ id: 't3', empName: 'Maingau', tariffVariant: 'Standard', priceTier: 'HIGH', pricePerKwh: 0.82, monthlyFeeEur: 0 }),
    row({ id: 't4', empName: 'ADAC e-Charge', tariffVariant: 'Standard', pricePerKwh: 0.55, monthlyFeeEur: 0 }),
  ],
  emps: ['EnBW', 'IONITY', 'Maingau'],
  cpos: ['EnBW', 'IONITY'],
}

let app: App | null = null
afterEach(() => {
  app?.unmount(); app = null
  document.body.innerHTML = ''
  overview.mockReset(); create.mockReset(); update.mockReset(); end.mockReset()
  vi.restoreAllMocks()
})

async function mount(): Promise<HTMLElement> {
  overview.mockResolvedValue(sample)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(AdminTariffsTab)
  app.mount(host)
  await nextTick(); await nextTick(); await nextTick()
  return host
}

const flush = async () => { for (let i = 0; i < 4; i++) await nextTick() }
const button = (host: HTMLElement, text: string) =>
  [...host.querySelectorAll('button')].find(b => b.textContent?.replace(/\s+/g, ' ').trim() === text)!
const rows = (host: HTMLElement) => [...host.querySelectorAll('[data-testid="tariff-row"]')].map(r => r.textContent!.replace(/\s+/g, ' '))
const input = (host: HTMLElement, name: string) => host.querySelector(`[name="${name}"]`) as HTMLInputElement

describe('AdminTariffsTab', () => {
  it('zeigt gültige Tarife mit Geltungsbereich, beendete erst auf Wunsch', async () => {
    const host = await mount()
    expect(rows(host)).toHaveLength(3)
    expect(rows(host)[0]).toContain('IONITY')
    expect(rows(host)[0]).toContain('eigenes Netz')
    expect(rows(host)[0]).toContain('39,0 ct')
    expect(rows(host)[0]).toContain('11,99 €')
    expect(rows(host)[1]).toContain('Tier HIGH')
    // ohne eigenes Netz im Katalog gilt der Tarif nirgends
    expect(rows(host)[2]).toContain('nirgends')

    ;(host.querySelector('[name="showEnded"]') as HTMLInputElement).click()
    await flush()
    expect(rows(host)).toHaveLength(4)
    expect(rows(host).join(' ')).toContain('bis 30.11.2025')
  })

  it('beendet einen Tarif zum gewählten Tag und lädt neu', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('2026-10-25')
    end.mockResolvedValue(row({ validUntil: '2026-10-25' }))
    const host = await mount()

    ;[...host.querySelectorAll('[data-testid="tariff-row"]')][0].querySelector('button[data-action="end"]')!.dispatchEvent(new Event('click'))
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
    }))
  })

  it('ändert einen Tarif mit vorbelegtem Formular', async () => {
    update.mockResolvedValue(row({ pricePerKwh: 0.41 }))
    const host = await mount()
    ;[...host.querySelectorAll('[data-testid="tariff-row"]')][0].querySelector('button[data-action="edit"]')!.dispatchEvent(new Event('click'))
    await flush()

    expect(input(host, 'empName').value).toBe('IONITY')
    const price = input(host, 'pricePerKwh'); price.value = '0.41'; price.dispatchEvent(new Event('input'))
    button(host, 'Speichern').click()
    await flush()

    expect(update).toHaveBeenCalledWith('t1', expect.objectContaining({ empName: 'IONITY', tariffVariant: 'Power', pricePerKwh: 0.41, monthlyFeeEur: 11.99 }))
  })
})

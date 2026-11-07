// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'
import type { MapStation, StationTariff } from '../../../api/chargingStationService'

const tariffs = vi.fn()
vi.mock('../../../api/chargingStationService', () => ({ chargingStationService: { tariffs: (...a: unknown[]) => tariffs(...a) } }))
const track = vi.fn()
vi.mock('../../../services/analytics', () => ({ analytics: { track: (...a: unknown[]) => track(...a) } }))

const { default: StationDetail } = await import('../StationDetail.vue')

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => { tariffs.mockReset(); track.mockReset() })

const station: MapStation = {
  id: '1', name: 'EWE Go', known: true, latitude: 52.3745, longitude: 9.7385, maxAcKw: 22, maxDcKw: 150, chargePoints: 4,
  plugTypes: ['Typ 2', 'CCS'], address: 'Georgstraße 1, 30159 Hannover', openingHours: '24/7', payment: 'Ladekarte; App',
  priceAc: null, priceDc: { pricePerKwh: 0.52, cardName: 'EWE Go', variant: null },
}
const tariff = (o: Partial<StationTariff>): StationTariff => ({
  name: 'EWE Go', variant: null, pricePerKwh: 0.52, monthlyFeeEur: 0, verifiedOn: '2026-11-01', direct: true,
  sessionFeeEur: 0, blockingFeePerMin: null, blockingFeeAfterMin: null, blockingFeeCapEur: null, ...o,
})

async function mount(props: Record<string, unknown>) {
  let back = 0
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StationDetail, { type: 'DC', distanceMeters: 250, apple: false, ...props, onBack: () => { back++ } })
  app.use(i18n)
  app.mount(host)
  for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() }
  return { host, back: () => back }
}
const text = (el: Element) => el.textContent!.replace(/\s+/g, ' ')

describe('StationDetail', () => {
  it('Registerdaten, Route und alle Tarife mit Gebühren, günstigste zuerst', async () => {
    tariffs.mockResolvedValue([
      tariff({}),
      tariff({ name: 'ADAC e-Charge', direct: false, pricePerKwh: 0.75, blockingFeePerMin: 0.15, blockingFeeAfterMin: 45, verifiedOn: '2026-11-03' }),
      tariff({ name: 'EnBW', variant: 'L', direct: false, pricePerKwh: 0.39, monthlyFeeEur: 11.99, sessionFeeEur: 0.49, blockingFeeCapEur: 12 }),
    ])
    const { host } = await mount({ station })

    expect(tariffs).toHaveBeenCalledWith('EWE Go', 'DC', 150, expect.anything())
    const t = text(host)
    expect(t).toContain('Georgstraße 1, 30159 Hannover')
    expect(t).toContain('CCS bis 150 kW · 4 Ladepunkte')
    expect(t).toContain('Öffnungszeiten: 24/7')
    expect(t).toContain('So viel kostet Laden hier')
    const rows = [...host.querySelectorAll('[data-testid="station-price-row"]')].map(text)
    expect(rows[0]).toContain('EWE Go')
    expect(rows[0]).toContain('Säule des Anbieters')
    expect(rows[0]).toContain('Keine Start- und Blockiergebühr')
    expect(rows[1]).toContain('Blockiergebühr ab 45 Min.: 0,15 € je Min.')
    expect(rows[2]).toContain('EnBW L')
    expect(rows[2]).toContain('Startgebühr 0,49 €')
    expect(rows[2]).toContain('Grundgebühr 11,99 € im Monat')
    expect(t).toContain('Preise ohne Grundgebühr, geprüft am 03.11.2026.')

    const route = host.querySelector<HTMLAnchorElement>('a[data-testid="station-route"]')!
    expect(route.href).toBe('https://www.google.com/maps/dir/?api=1&destination=52.3745,9.7385')
    expect(route.target).toBe('_blank')
    route.addEventListener('click', e => e.preventDefault())
    route.click()
    expect(track).toHaveBeenCalledWith('station_route_clicked', expect.anything())
  })

  it('ohne Tarif bleibt die Säule mit Registerdaten, der Preisabschnitt entfällt', async () => {
    tariffs.mockResolvedValue(null)
    const { host } = await mount({ station })

    expect(text(host)).toContain('Georgstraße 1')
    expect(text(host)).not.toContain('So viel kostet Laden hier')
  })

  it('unbekannter Betreiber: keine Preisabfrage', async () => {
    const { host } = await mount({ station: { ...station, known: false, name: 'Stadtwerke Musterstadt' } })

    expect(tariffs).not.toHaveBeenCalled()
    expect(text(host)).not.toContain('So viel kostet Laden hier')
  })

  it('iPhone: Route über Apple Karten', async () => {
    tariffs.mockResolvedValue(null)
    const { host } = await mount({ station, apple: true })

    expect(host.querySelector<HTMLAnchorElement>('a[data-testid="station-route"]')!.href)
      .toBe('https://maps.apple.com/?daddr=52.3745,9.7385')
  })

  it('Zurück-Pfeil meldet zurück zur Liste', async () => {
    tariffs.mockResolvedValue(null)
    const { host, back } = await mount({ station })

    host.querySelector<HTMLButtonElement>('button[data-testid="station-back"]')!.click()
    expect(back()).toBe(1)
  })

  it('Preise laden nicht: Hinweis mit Erneut versuchen', async () => {
    tariffs.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([tariff({})])
    const { host } = await mount({ station })

    expect(text(host)).toContain('Preise konnten nicht geladen werden.')
    host.querySelector<HTMLButtonElement>('button[data-testid="station-prices-retry"]')!.click()
    for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() }
    expect(host.querySelectorAll('[data-testid="station-price-row"]')).toHaveLength(1)
  })
})

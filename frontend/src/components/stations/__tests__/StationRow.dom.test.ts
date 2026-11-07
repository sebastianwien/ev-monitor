// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { i18n } from '../../../i18n'
import StationRow from '../StationRow.vue'
import type { MapStation } from '../../../api/chargingStationService'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const station: MapStation = {
  id: '1', name: 'EWE Go', known: true, latitude: 52.37, longitude: 9.73, maxAcKw: 22, maxDcKw: 150, chargePoints: 4,
  plugTypes: ['Typ 2', 'CCS'], address: 'Georgstraße 1, 30159 Hannover', openingHours: null, payment: null,
  priceAc: { pricePerKwh: 0.52, cardName: 'EWE Go', variant: null },
  priceDc: { pricePerKwh: 0.519, cardName: 'EWE Go', variant: null },
}

function mount(props: Record<string, unknown>) {
  let selected = 0
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StationRow, { ...props, onSelect: () => { selected++ } })
  app.use(i18n)
  app.mount(host)
  return { host, selected: () => selected }
}

describe('StationRow', () => {
  it('Name, Entfernung, Stecker der Ladeart, Ladepunkte, Preis als Geld-Kapsel und Karte', () => {
    const { host } = mount({ station, type: 'DC', distanceMeters: 248 })
    const text = host.textContent!.replace(/\s+/g, ' ')
    expect(text).toContain('EWE Go')
    expect(text).toContain('250 m · CCS bis 150 kW · 4 Ladepunkte')
    expect(host.querySelector('.ci-money')?.textContent?.replace(/\s/g, ' ')).toBe('0,52 €')
  })

  it('Normalladen zeigt Typ 2 und die AC-Leistung', () => {
    const { host } = mount({ station, type: 'AC', distanceMeters: 1149 })
    expect(host.textContent!.replace(/\s+/g, ' ')).toContain('1,1 km · Typ 2 bis 22 kW · 4 Ladepunkte')
  })

  it('ohne Katalogpreis keine Geld-Kapsel', () => {
    const { host } = mount({ station: { ...station, known: false, priceDc: null }, type: 'DC', distanceMeters: 100 })
    expect(host.querySelector('.ci-money')).toBeNull()
  })

  it('ist ein Knopf und meldet die Wahl', () => {
    const { host, selected } = mount({ station, type: 'DC', distanceMeters: 100 })
    host.querySelector('button')!.click()
    expect(selected()).toBe(1)
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App, type Component } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import SheetCharging from '../SheetCharging.vue'
import SheetStandby from '../SheetStandby.vue'
import SheetCo2 from '../SheetCo2.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
function mount(c: Component, props: Record<string, unknown>) {
  const host = document.createElement('div'); document.body.appendChild(host)
  app = createApp(c, props); app.use(createPinia()).use(i18n); app.mount(host)
  return host.textContent!.replace(/\s+/g, ' ')
}

describe('Sheets der neuen Kacheln', () => {
  it('Laden: Aufpreis je kWh beim Schnellladen, unbekannte Ladeart als Hinweis', () => {
    const text = mount(SheetCharging, { split: { acKwh: 300, dcKwh: 100, unknownKwh: 12, acEurPerKwh: 0.3, dcEurPerKwh: 0.61 } })
    expect(text).toMatch(/kostet dich 0,31\s€ mehr/)
    expect(text).toContain('Bei 12 kWh ist die Ladeart nicht bekannt.')
  })
  it('Laden: kein Aufpreis-Satz ohne beide Preise', () => {
    expect(mount(SheetCharging, { split: { acKwh: 300, dcKwh: 100, unknownKwh: 0, acEurPerKwh: 0.3, dcEurPerKwh: null } })).not.toContain('mehr')
  })
  it('Stand: je Stand Dauer und kWh', () => {
    const text = mount(SheetStandby, { totalKwh: 3.3, totalEur: 1.05, gaps: [{ kwh: 2.5, from: '2026-10-03T08:30:00Z', to: '2026-10-06T08:00:00Z', eur: 0.75 }, { kwh: 0.8, from: '2026-10-02T20:00:00Z', to: '2026-10-03T06:00:00Z', eur: null }] })
    expect(text).toMatch(/~1,05\s€/)
    expect(text).toMatch(/~0,75\s€/)
    expect(text).toContain('3 Tage')
    expect(text).not.toContain('3 Tagen')
    expect(text).toContain('10 Stunden')
    expect(text).toContain('2,5 kWh')
  })
  it('CO₂: drei Zeilen in g/km', () => {
    const text = mount(SheetCo2, { tile: { id: 'co2', value: 322.4, delta: null, deltaPercent: null, sentiment: 'good', spark: [], sparkVariant: 'bars', co2: { petrolGPerKm: 178, gridGPerKm: 68, greenGPerKm: 5.4 } } })
    expect(text).toContain('322 kg CO₂ weniger')
    expect(text).toContain('Ökostrom')
    expect(text).toContain('5 g/km')
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import SheetRange from '../sheets/SheetRange.vue'
import type { RangeEstimate } from '../../../composables/storyEnergy'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const range = (over: Partial<RangeEstimate> = {}): RangeEstimate => ({
  consumption: 26.9, source: 'OWN_DRIVE', km: 201, emptyKm: 226,
  windows: [{ socMax: 90, socMin: 10, recommended: true, km: 201 }],
  summer: { consumption: 17.4, km: 311 }, winter: null, peerWinter: null, ...over,
})

function mount(r: RangeEstimate) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(SheetRange, { range: r, capacityKwh: 67.7, modelName: 'Ioniq 5' })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host.textContent!.replace(/\s+/g, ' ')
}

describe('SheetRange', () => {
  it('ohne Winterwert: Sommerzeile, kein Platzhalter, Details offen', () => {
    const text = mount(range())
    expect(text).toMatch(/Sommer\s?311\s?km/)
    expect(text).toMatch(/Notfalls bis ganz leer: etwa 226\s?km/)
    expect(text).toContain('90 → 10 %')
    expect(text).not.toContain('noch kein Wert')
    expect(text).not.toContain('Winter')
  })

  it('Winter anderer Fahrer: mit Tilde und Herkunft', () => {
    const text = mount(range({ peerWinter: { consumption: 31.5, km: 172 } }))
    expect(text).toMatch(/Winter\s?~172\s?km/)
    expect(text).toContain('Verbrauch anderer Ioniq 5-Fahrer')
  })

  it('eigener Winter: ohne Tilde und ohne Herkunftshinweis', () => {
    const text = mount(range({ winter: { consumption: 30, km: 180 } }))
    expect(text).toMatch(/Winter\s?180\s?km/)
    expect(text).not.toContain('anderer')
  })

  it('eigene Fahrten: Satz unter der Zahl ohne Zusatz, Basis "Fahrten ab 20 km"', () => {
    const text = mount(range())
    expect(text).toContain('so wie du in den letzten 4 Wochen gefahren bist')
    expect(text).toContain('Ø 4 Wochen, Fahrten ab 20 km')
    expect(text).not.toContain('Gerechnet mit dem Verbrauch aus deinen Ladungen')
  })

  it('ohne Fahrten: Hinweis auf den Strom im Stand, Basis "aus Ladungen"', () => {
    const text = mount(range({ source: 'OWN_CHARGES' }))
    expect(text).not.toContain('so wie du')
    expect(text).toContain('Er enthält auch Strom, den das Auto im Stand braucht, die Reichweite ist daher eher zu kurz.')
    expect(text).toContain('Ø 4 Wochen aus Ladungen')
  })

  it('Modellschnitt: Zahl mit Tilde und Herkunftssatz', () => {
    const text = mount(range({ source: 'MODEL_DRIVE' }))
    expect(text).toMatch(/~201\s?km/)
    expect(text).toContain('Verbrauch beim Fahren anderer Ioniq 5-Fahrer, auf deinen Akku gerechnet.')
    expect(text).toContain('Ø anderer Ioniq 5-Fahrer')
  })
})

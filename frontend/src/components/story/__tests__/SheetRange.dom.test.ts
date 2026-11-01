// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import SheetRange from '../sheets/SheetRange.vue'
import type { RangeEstimate, RangeSeason } from '../../../composables/storyEnergy'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const own = (km: number, consumption: number): RangeSeason => ({ km, consumption, source: 'OWN_DRIVE', scope: null })
const range = (over: Partial<RangeEstimate> = {}): RangeEstimate => ({
  ...own(201, 26.9), summer: own(311, 17.4), winter: null, ...over,
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
  it('eigene Fahrten: Zahl ohne Tilde, Fenster 100 auf 10 %, Sommerzeile ohne Herkunft, kein Winter', () => {
    const text = mount(range())
    expect(text).toContain('201 km')
    expect(text).not.toContain('~201')
    expect(text).toContain('pro Ladung von 100 auf 10 %, so wie du in den letzten 4 Wochen gefahren bist')
    expect(text).toMatch(/Sommer\s?311\s?km/)
    expect(text).toContain('Ø 4 Wochen, Fahrten ab 20 km')
    expect(text).not.toContain('Winter')
    expect(text).not.toContain('Notfalls')
    expect(text).not.toContain('90 → 10')
  })

  it('Winter anderer Fahrer des Modells: Tilde und Herkunft in der Zeile und in der Verbrauchstabelle', () => {
    const text = mount(range({ winter: { km: 172, consumption: 31.5, source: 'PEER_DRIVE', scope: 'MODEL' } }))
    expect(text).toMatch(/Winter\s?andere Ioniq 5-Fahrer\s?~172\s?km/)
    expect(text).toContain('Winter, November bis Februar, alle Jahre, andere Ioniq 5-Fahrer')
  })

  it('eigener Winter: ohne Tilde und ohne Herkunft', () => {
    const text = mount(range({ winter: own(180, 30) }))
    expect(text).toMatch(/Winter\s?180\s?km/)
    expect(text).not.toContain('anderer')
    expect(text).not.toContain('andere ')
  })

  it('ohne Fahrten: Hinweis auf den Strom im Stand, Basis "aus Ladungen", Tilde', () => {
    const text = mount(range({ source: 'OWN_CHARGES' }))
    expect(text).toContain('~201 km')
    expect(text).toContain('pro Ladung von 100 auf 10 %')
    expect(text).not.toContain('so wie du')
    expect(text).toContain('Er enthält auch Strom, den das Auto im Stand braucht, die Reichweite ist daher eher zu kurz.')
    expect(text).toContain('Ø 4 Wochen aus Ladungen')
  })

  it('andere Fahrer derselben Variante: Zahl mit Tilde und Herkunftssatz', () => {
    const text = mount(range({ source: 'PEER_DRIVE', scope: 'VARIANT' }))
    expect(text).toMatch(/~201\s?km/)
    expect(text).toContain('Verbrauch beim Fahren anderer Ioniq 5-Fahrer mit deiner Variante, auf deinen Akku gerechnet.')
    expect(text).toContain('Ø anderer Fahrer deiner Variante')
  })

  it('Ladungen anderer Fahrer: Tilde und Satz zur Herkunft, Schätzung aus der Herstellerangabe als Saisonzeile', () => {
    const text = mount(range({ source: 'PEER_CHARGES', summer: { km: 240, consumption: 22, source: 'WLTP_ESTIMATE', scope: null } }))
    expect(text).toMatch(/~201\s?km/)
    expect(text).toContain('Verbrauch aus den Ladungen anderer Ioniq 5-Fahrer, mit Strom im Stand, auf deinen Akku gerechnet.')
    expect(text).toContain('Ø Ladungen anderer Ioniq 5-Fahrer')
    expect(text).toMatch(/Sommer\s?geschätzt\s?~240\s?km/)
  })
})

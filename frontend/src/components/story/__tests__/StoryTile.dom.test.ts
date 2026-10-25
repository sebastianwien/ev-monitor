// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useCountryStore } from '../../../stores/country'
import { i18n } from '../../../i18n'
import StoryTile from '../StoryTile.vue'
import type { StoryTile as Tile } from '../../../composables/storyModel'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const cost = (over: Partial<Tile> = {}): Tile => ({
  id: 'cost', value: 0, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [0, 18, 0], sparkVariant: 'line', ...over,
})

function mount(tile: Tile) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryTile, { tile })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host.textContent!.replace(/\s+/g, ' ')
}

describe('StoryTile Kosten', () => {
  it('Ladungen ohne Preis: nennt ihre Zahl statt eines Vergleichs mit dem Vormonat', () => {
    const text = mount(cost({ pricelessCharges: 2, sentiment: 'warn' }))
    expect(text).toContain('2 ohne Preis')
    expect(text).not.toContain('Vormonat')
  })

  it('Vormonat unvollständig: kein Vergleich, auch nicht "kein Vormonat"', () => {
    const text = mount(cost({ value: 21, previousIncomplete: true }))
    expect(text).not.toContain('Vormonat')
  })

  it('laufender Monat unter der Strommenge des Vormonats: dezente Prognose statt Ersparnis', () => {
    const text = mount(cost({ value: 60, projection: { eur: 120.4, previousEur: 100 } }))
    expect(text).toMatch(/Prognose ~120\s€, Vormonat 100\s€/)
    expect(text).not.toContain('zum Vormonat')
  })

  it('ohne Vormonat bleibt "kein Vormonat"', () => {
    expect(mount(cost({ value: 21 }))).toContain('kein Vormonat')
  })

  it('wenige Ladungen: kein Chip in der Kachel, die Zahl steht in der Qualitätszeile', () => {
    expect(mount(cost({ value: 21, thinCharges: 2 }))).not.toContain('nur 2 Ladungen')
  })
})

describe('StoryTile Vergleich mit anderen', () => {
  const peer = (over: Partial<Tile> = {}): Tile => ({
    id: 'peer', value: 26.9, delta: null, deltaPercent: 44, sentiment: 'warn', spark: [18.6, 26.9], sparkVariant: 'range', peerCount: 11, ...over,
  })

  it('nennt Prozent und Zahl der Fahrer', () => {
    expect(mount(peer())).toContain('44 % mehr als 11 Fahrer')
  })

  it('bei wenigen Ladungen nur den Schnitt der anderen, kein Prozentsatz', () => {
    const text = mount(peer({ thinCharges: 2 }))
    expect(text).toContain('Schnitt 18,6, 11 Autos')
    expect(text).not.toContain('%')
  })

  it('feste Höhe, Inhalt oben: Grafik sitzt am unteren Rand', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(StoryTile, { tile: peer() })
    app.use(createPinia()).use(i18n)
    app.mount(host)
    const btn = host.querySelector('button')!
    expect(btn.className).toContain('h-[150px]')
    expect(btn.className).toContain('md:h-[160px]')
    expect(host.querySelector('[data-role="graphic"]')?.className).toContain('mt-auto')
  })
})

describe('StoryTile Akku und Reichweite', () => {
  it('das Label ist die Frage, mit Fragezeichen', () => {
    expect(mount(cost({ value: 21 }))).toContain('Was kostet mich das Fahren?')
  })

  it('geschätzter Akkuwert mit Tilde und Hinweis, eigene Angabe ohne', () => {
    const battery = (estimated: boolean): Tile => ({ id: 'battery', value: 92, delta: null, deltaPercent: null, sentiment: 'good', spark: [100, 92], sparkVariant: 'range', odometerKm: 66265, estimated })
    const est = mount(battery(true))
    expect(est).toContain('~92')
    expect(est).toMatch(/~92\s*% SoH\s*geschätzt/)
    app!.unmount(); app = null
    const manual = mount(battery(false))
    expect(manual).not.toContain('~')
    expect(manual).not.toContain('geschätzt')
  })

  it('Reichweite nennt Basis und Ladefenster', () => {
    const text = mount({ id: 'range', value: 201, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [201], sparkVariant: 'bars',
      range: { consumption: 21, km: 201, windows: [{ socMax: 90, socMin: 10, recommended: true, km: 201 }], summer: null, winter: null } })
    expect(text).toContain('Ø letzte 4 Wochen, 90 → 10 %')
  })
})

describe('StoryTile Währung', () => {
  it('Pfund steht wie überall vor der Zahl', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const pinia = createPinia()
    setActivePinia(pinia)
    useCountryStore().setCountry('GB')
    app = createApp(StoryTile, { tile: cost({ value: 151 }) })
    app.use(pinia).use(i18n)
    app.mount(host)
    expect(host.textContent!.replace(/\s+/g, ' ')).toContain('£')
    expect(host.querySelector('b')!.textContent).toMatch(/^£\d/)
    try { localStorage.removeItem('ev-country') } catch { /* egal */ }
  })
})

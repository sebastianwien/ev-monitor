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

describe('StoryTile Fahrten', () => {
  const trips = (over: Partial<Tile> = {}): Tile => ({
    id: 'trips', value: 18.2, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [], sparkVariant: 'bars',
    tripMonth: { trips: 14, km: 151, avgKm: 10.8, driveKwhPer100km: 18.2, routeShares: [{ key: 'CITY', pct: 70 }, { key: 'HIGHWAY', pct: 30 }], mainRoute: 'CITY', lengthShares: [{ key: 'SHORT', pct: 64 }, { key: 'MEDIUM', pct: 36 }], longestKm: 42, tempMin: null, tempMax: null, standbyKwh: null, standbyLocked: true, standbyGaps: [] },
    ...over,
  })

  it('Frage, Antriebsverbrauch, Satz mit Fahrten, Ø-Länge und Schwerpunkt, Balken nach Fahrtlänge', () => {
    const text = mount(trips())
    expect(text).toContain('Wie fahre ich?')
    expect(text).toContain('18,2')
    expect(text).toMatch(/14 Fahrten, Ø 11\skm, meist Stadt/)
    expect(text).toContain('<5 km 64 %')
    expect(text).toContain('5-30 km 36 %')
  })
})

describe('StoryTile CO₂, Laden, Stand', () => {
  const base = { delta: null, deltaPercent: null, sentiment: 'neutral' as const, spark: [], sparkVariant: 'bars' as const }
  it('CO₂: gesparte kg und Benziner gegen dein Auto in g/km', () => {
    const text = mount({ ...base, id: 'co2', value: 322.4, co2: { petrolGPerKm: 178, gridGPerKm: 68, greenGPerKm: 5.4 } })
    expect(text).toContain('Wie viel CO₂ spare ich?')
    expect(text).toMatch(/322\s?kg CO₂ weniger/)
    expect(text).toContain('178 g/km')
    expect(text).toContain('68 g/km')
  })
  it('Laden: Anteil schnell, Preis je kWh je Art, Beschriftung im Balken', () => {
    const text = mount({ ...base, id: 'charging', value: 25, charging: { acKwh: 300, dcKwh: 100, unknownKwh: 0, acEurPerKwh: 0.3, dcEurPerKwh: 0.61 } })
    expect(text).toMatch(/25\s?% schnell/)
    expect(text).toMatch(/Normal 0,30\s€, schnell 0,61\s€ je kWh/)
    expect(text).toContain('Normal (AC) 75 %')
    expect(text).toContain('Schnell (DC) 25 %')
  })
  it('Laden ohne Preise: kWh schnell geladen', () => {
    const text = mount({ ...base, id: 'charging', value: 25, charging: { acKwh: 300, dcKwh: 100, unknownKwh: 0, acEurPerKwh: null, dcEurPerKwh: null } })
    expect(text).toContain('100 kWh schnell geladen')
  })
  it('Stand: Verlust im Monat und der größte Stand', () => {
    const tripMonth = { trips: 4, km: 40, avgKm: 10, lengthShares: [], driveKwhPer100km: 17, routeShares: [], mainRoute: null, longestKm: 10, tempMin: null, tempMax: null,
      standbyKwh: 3.3, standbyLocked: false, standbyGaps: [{ kwh: 2.5, from: '2026-10-03T08:30:00Z', to: '2026-10-06T08:00:00Z' }, { kwh: 0.8, from: '2026-10-02T08:30:00Z', to: '2026-10-03T08:00:00Z' }] }
    const text = mount({ ...base, id: 'standby', value: 3.3, tripMonth })
    expect(text).toMatch(/3,3\s?kWh/)
    expect(text).toContain('am meisten 2,5 kWh in 3 Tagen')
  })
})

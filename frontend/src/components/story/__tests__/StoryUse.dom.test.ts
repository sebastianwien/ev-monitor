// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App, type Component } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryTile from '../StoryTile.vue'
import SheetFlow from '../sheets/SheetFlow.vue'
import type { StoryTile as Tile } from '../../../composables/storyModel'
import type { EnergyUse } from '../../../composables/storyEnergy'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

// Ihle Oktober: 46 Fahrten, Stand 34 kWh, eine von zwei Ladungen hochgerechnet, alles öffentlich
const use = (over: Partial<EnergyUse> = {}): EnergyUse => ({
  driveKwh: 33.5, standbyKwh: 34, chargeLossKwh: 3, trips: 46, charges: 2, estimatedCharges: 1,
  lossEur: 10.73, standbyEur: 9.86, chargeEur: 0.87, homePct: 0,
  shares: [{ key: 'drive', pct: 48 }, { key: 'standby', pct: 48 }, { key: 'charge', pct: 4 }], ...over,
})
const tile = (u: EnergyUse): Tile => ({
  id: 'flow', value: u.lossEur ?? (u.standbyKwh ?? 0) + u.chargeLossKwh, delta: null, deltaPercent: null,
  sentiment: 'neutral', spark: [], sparkVariant: 'range', use: u,
})

function mount(c: Component, props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(c, props)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element) => el.textContent!.replace(/\s+/g, ' ')

describe('Strom-Kachel mit Fahren, Stand, Laden', () => {
  it('Verlust in Euro, Satz mit Stand und Laden, Balken mit drei Flächen, Ladeort als Zeile', () => {
    const t = text(mount(StoryTile, { tile: tile(use()) }))
    expect(t).toMatch(/11\s*€ Verlust/)
    expect(t).toContain('34 kWh im Stand, ~3 kWh beim Laden')
    expect(t).toContain('Fahren 48 %')
    expect(t).toContain('Stand 48 %')
    expect(t).toContain('Laden 4 %')
    expect(t).toContain('Geladen: 100 % öffentlich')
  })

  it('Ladeverlust nur gemessen: ohne Tilde; gemischter Ladeort mit beiden Anteilen', () => {
    const t = text(mount(StoryTile, { tile: tile(use({ estimatedCharges: 0, homePct: 70 })) }))
    expect(t).toContain('34 kWh im Stand, 3 kWh beim Laden')
    expect(t).toContain('Geladen: 70 % zuhause, 30 % öffentlich')
  })

  it('ohne Preis: Verlust in kWh', () => {
    const t = text(mount(StoryTile, { tile: tile(use({ lossEur: null, standbyEur: null, chargeEur: null })) }))
    expect(t).toMatch(/37\s*kWh Verlust/)
  })

  it('Stand gesperrt: schraffierte Fläche ohne Prozent, Satz nur Laden', () => {
    const host = mount(StoryTile, { tile: tile(use({
      standbyKwh: null, standbyEur: null, lossEur: 0.87,
      shares: [{ key: 'drive', pct: 92 }, { key: 'standby', pct: null }, { key: 'charge', pct: 8 }],
    })) })
    const t = text(host)
    expect(t).toContain('~3 kWh beim Laden')
    expect(t).not.toContain('im Stand')
    expect(t).not.toMatch(/Stand \d/)
    expect(host.querySelectorAll('[data-locked]').length).toBe(1)
  })
})

describe('Sheet "Wo bleibt mein Strom?" mit Fahren, Stand, Laden', () => {
  it('drei Posten in kWh mit Herkunft, Euro für Stand und Laden', () => {
    const t = text(mount(SheetFlow, { use: use() }))
    expect(t).toContain('aus 46 Fahrten')
    expect(t).toContain('zwischen den Fahrten, Auto stand')
    expect(t).toContain('aus 2 Ladungen, 1 davon geschätzt')
    expect(t).toMatch(/33,5\s*kWh/)
    expect(t).toMatch(/Stand ~10\s*€, Laden ~1\s*€/)
  })

  it('Stand gesperrt: Hinweis auf Analytics statt Zahl', () => {
    const t = text(mount(SheetFlow, { use: use({ standbyKwh: null, standbyEur: null, shares: [{ key: 'drive', pct: 92 }, { key: 'standby', pct: null }, { key: 'charge', pct: 8 }] }) }))
    expect(t).toContain('Analytics zeigt dir, wie viel dein Auto im Stand verbraucht')
    expect(t).not.toContain('34')
  })
})

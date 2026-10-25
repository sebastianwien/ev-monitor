// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryMomentHero from '../StoryMomentHero.vue'
import { HOUR, DAY, type Moment } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const NOW = new Date(2026, 9, 24, 12).getTime()
const b = { at: NOW, lifetimeMs: DAY, actionable: false, dismissible: false }
const text = (host: HTMLElement) => host.textContent!.replace(/ /g, ' ').replace(/\s+/g, ' ')

function mount(moment: Moment, on: Record<string, (...a: unknown[]) => void> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryMomentHero, { moment, carId: 'c1', carName: 'Tesla Model 3', now: NOW, ...on })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

describe('StoryMomentHero', () => {
  it('gerade geladen: Energie, SoC, Reichweite und Preis nachtragen', () => {
    const host = mount({ ...b, kind: 'just_charged', key: 'k', actionable: true, logId: 'l1', kwh: 28.5, socFrom: 35, socTo: 80, rangeKm: 210, costEur: null })
    expect(text(host)).toContain('Gerade geladen')
    expect(text(host)).toContain('28,5 kWh, 35 → 80 %. Reicht für etwa 210 km.')
    expect(host.querySelector('a')!.getAttribute('href')).toBe('/logs?car=c1&nachtragen=preis')
  })

  it('zu teuer geladen: Preis, was andere Fahrer zahlen, Karte ohne Namen, Preise ansehen', () => {
    const onAct = vi.fn()
    const m = { ...b, kind: 'expensive', key: 'k', at: NOW - DAY, lifetimeMs: 3 * DAY, logId: 'l1', cpoName: 'IONITY', pricePerKwh: 0.789, medianPricePerKwh: 0.52, cardFromPricePerKwh: 0.49 } as const
    const host = mount(m, { onAct })
    expect(text(host)).toContain('Teurer als üblich')
    expect(text(host)).toContain('Gestern 79 ct/kWh bei IONITY. Andere Fahrer zahlen dort im Schnitt 52 ct/kWh, mit einer Ladekarte ab 49 ct/kWh.')
    host.querySelector('button')!.click()
    expect(onAct).toHaveBeenCalledWith('prices')
    app!.unmount(); app = null
    const plain = mount({ ...m, cardFromPricePerKwh: null })
    expect(text(plain)).toContain('Andere Fahrer zahlen dort im Schnitt 52 ct/kWh.')
    expect(text(plain)).not.toContain('Ladekarte')
  })

  it('Lücke: fragt nach der einen Ladung, mit "Weiß ich nicht mehr"', () => {
    const onAct = vi.fn()
    const host = mount({ ...b, kind: 'gap', key: 'k', at: NOW - 2 * DAY, actionable: true, logId: 'l1', count: 1, kwh: 28.5, since: NOW - 2 * DAY }, { onAct })
    expect(text(host)).toContain('Was haben die 28,5 kWh vom 22.10. gekostet?')
    expect(host.querySelector('a')!.getAttribute('href')).toBe('/logs?car=c1&nachtragen=preis')
    ;[...host.querySelectorAll('button')].find(x => x.textContent!.includes('Weiß ich nicht mehr'))!.click()
    expect(onAct).toHaveBeenCalledWith('price_unknown')
  })

  it('Lücke: mehrere Ladungen nur mit Zahl, entschieden wird in der Liste', () => {
    const host = mount({ ...b, kind: 'gap', key: 'k', at: NOW - DAY, actionable: true, logId: 'l1', count: 3, kwh: 20, since: NOW - 12 * DAY })
    expect(text(host)).toContain('3 Ladungen seit dem 12.10. haben noch keinen Preis.')
    expect(text(host)).toContain('Preise nachtragen')
    expect(text(host)).not.toContain('Weiß ich nicht mehr')
  })

  it('Meilenstein: Ladungen mit CO₂, Jahrestag in der Mehrzahl, Teilen', () => {
    const onAct = vi.fn()
    const host = mount({ ...b, kind: 'milestone', key: 'k', metric: 'charges', value: 100, charges: 100, km: 21_900, co2Kg: 2400 }, { onAct })
    expect(text(host)).toContain('Meilenstein')
    expect(text(host)).toContain('100 Ladungen.')
    expect(text(host)).toContain('2,4 t CO₂ weniger als mit Benzin.')
    host.querySelector('button')!.click()
    expect(onAct).toHaveBeenCalledWith('share')
    app!.unmount(); app = null
    const years = mount({ ...b, kind: 'milestone', key: 'k', metric: 'years', value: 2, charges: 1250, km: 31_000, co2Kg: null })
    expect(text(years)).toContain('2 Jahre: 31.000 km und 1.250 Ladungen.')
  })

  it('lange Fahrt mit Stopp und Kosten, Tag relativ', () => {
    const host = mount({ ...b, kind: 'long_trip', key: 'k', at: NOW - 20 * HOUR, km: 340, stops: 1, costEur: 18 })
    expect(text(host)).toContain('Gestern: 340 km, 1 Ladestopp für 18 €.')
  })

  it('Akku: Schätzung mit Tilde und Hinweis, eigene Angabe ohne', () => {
    const est = mount({ ...b, kind: 'battery', key: 'k', sohPercent: 92, estimated: true, previousSohPercent: 93.4, previousAt: NOW - 186 * DAY })
    expect(text(est)).toContain('Dein Akku: ~92 %.')
    expect(text(est)).toContain('Vor 6 Monaten: ~93 %.')
    expect(text(est)).toContain('geschätzt')
    app!.unmount(); app = null
    const manual = mount({ ...b, kind: 'battery', key: 'k', sohPercent: 92, estimated: false, previousSohPercent: null, previousAt: null })
    expect(text(manual)).toContain('Dein Akku: 92 %.')
    expect(text(manual)).not.toContain('geschätzt')
  })

  it('Stille: AutoSync-Satz und Wegklicken', () => {
    const onDismiss = vi.fn()
    const host = mount({ ...b, kind: 'silence', key: 'k', lifetimeMs: Infinity, actionable: true, dismissible: true, days: 16, kwh: 28.5, autoSync: true }, { onDismiss })
    expect(text(host)).toContain('Letzte Ladung vor 16 Tagen: 28,5 kWh.')
    expect(text(host)).toContain('Dein Tesla Model 3 kann Ladungen und Fahrten selbst eintragen.')
    ;[...host.querySelectorAll('button')].find(x => x.textContent!.includes('Alles erfasst'))!.click()
    expect(onDismiss).toHaveBeenCalled()
  })

  it('Monatswechsel: Satz des Vormonats, Benzin, Teilen meldet die Handlung', () => {
    const onAct = vi.fn()
    const host = mount({ ...b, kind: 'month_change', key: 'k', year: 2026, month: 9, km: 279, eur: 33, charges: 6, petrolEur: 42 }, { onAct })
    expect(text(host)).toContain('September: 279 km für 33 €.')
    expect(text(host)).toContain('Mit Benzin wären es 42 €.')
    host.querySelector('button')!.click()
    expect(onAct).toHaveBeenCalledWith('share')
  })
})

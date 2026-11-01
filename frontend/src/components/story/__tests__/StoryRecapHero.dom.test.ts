// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryRecapHero from '../StoryRecapHero.vue'
import type { Moment, RecapFacts } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const NOW = new Date(2026, 9, 2, 12).getTime()

const facts: RecapFacts = { kwh: 96, ac: { kwh: 71, eur: 21.3 }, dc: { kwh: 25, eur: 15 }, co2Kg: 45.4 }
const recap = (f: RecapFacts | null): Moment => ({
  at: NOW, lifetimeMs: Infinity, actionable: false, dismissible: true, key: 'mc',
  kind: 'month_change', year: 2026, month: 9, km: 512, eur: 61, charges: 9, savingEur: 6.4, places: [], peer: null, facts: f,
})

function mount(moment: Moment, events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryRecapHero, { moment, carId: 'c1', carName: 'VW ID.3', onAct: (id: string) => events.push(['act', id]) })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
/** Zeilen aus mehreren Elementen: Kinder mit Leerzeichen getrennt, wie sie dastehen. */
const all = (host: Element, id: string) => [...host.querySelectorAll(`[data-testid="${id}"]`)].map(el => [...el.children].map(text).join(' '))

describe('StoryRecapHero', () => {
  it('zeigt Satz, Ersparnis, €/100 km, Energie, Ladungen, AC und DC mit Kosten und das gesparte CO₂', () => {
    const host = mount(recap(facts))
    expect(text(host)).toContain('September: 512 km für 61 €.')
    expect(text(host)).toContain('Gegenüber einem Verbrenner: 6 € gespart.')
    expect(all(host, 'recap-fact')).toEqual(['6 € Gespart', '11,91 € pro 100 km', '96 kWh Geladen', '9 Ladungen'])
    expect(all(host, 'recap-split')).toEqual(['Normal (AC) 21 € 71 kWh', 'Schnell (DC) 15 € 25 kWh'])
    expect(text(host.querySelector('[data-testid="recap-co2"]'))).toBe('45 kg CO₂ weniger als mit einem Benziner.')
    expect([...host.querySelectorAll('[data-testid="recap-co2"] strong')].map(text)).toEqual(['45'])
  })

  it('führt beim Tippen auf die ganze Karte zum Vormonat und bietet kein Teilen an', () => {
    const events: unknown[][] = []
    const host = mount(recap(facts), events)
    expect(host.querySelector('button')).toBeNull()
    ;(host.querySelector('[data-testid="story-recap-hero"]') as HTMLElement).click()
    expect(events).toEqual([['act', 'prev_month']])
    expect(text(host)).not.toMatch(/teilen/i)
  })

  it('ohne Zahlen des Vormonats ohne Energie, ohne Aufteilung und CO₂', () => {
    const host = mount(recap(null))
    expect(all(host, 'recap-fact')).toEqual(['6 € Gespart', '11,91 € pro 100 km', '9 Ladungen'])
    expect(host.querySelector('[data-testid="recap-split"]')).toBeNull()
    expect(host.querySelector('[data-testid="recap-co2"]')).toBeNull()
  })

  it('lässt Kosten weg, wo der Preis fehlt', () => {
    const host = mount(recap({ ...facts, ac: { kwh: 71, eur: null }, dc: null }))
    expect(all(host, 'recap-split')).toEqual(['Normal (AC) 71 kWh'])
  })
})

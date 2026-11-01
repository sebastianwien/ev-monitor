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
  it('zeigt Satz, Ersparnis, Strecke, Energie, Ladungen, AC und DC mit Kosten und das gesparte CO₂', () => {
    const host = mount(recap(facts))
    expect(text(host)).toContain('September: 512 km für 61 €.')
    expect(text(host)).toContain('Gegenüber einem Verbrenner: 6 € gespart.')
    expect(all(host, 'recap-fact')).toEqual(['512 km Strecke', '96 kWh Geladen', '9 Ladungen'])
    expect(all(host, 'recap-split')).toEqual(['Normal (AC) 71 kWh 21 €', 'Schnell (DC) 25 kWh 15 €'])
    expect(text(host.querySelector('[data-testid="recap-co2"]'))).toBe('45 kg CO₂ weniger als mit einem Benziner.')
    expect([...host.querySelectorAll('[data-testid="recap-co2"] strong')].map(text)).toEqual(['45'])
  })

  it('führt zum Vormonat und bietet kein Teilen an', () => {
    const events: unknown[][] = []
    const host = mount(recap(facts), events)
    const link = [...host.querySelectorAll('button')].find(b => text(b) === 'September ansehen')!
    link.click()
    expect(events).toEqual([['act', 'prev_month']])
    expect(text(host)).not.toMatch(/teilen/i)
  })

  it('ohne Zahlen des Vormonats nur Strecke und Ladungen, ohne Aufteilung und CO₂', () => {
    const host = mount(recap(null))
    expect(all(host, 'recap-fact')).toEqual(['512 km Strecke', '9 Ladungen'])
    expect(host.querySelector('[data-testid="recap-split"]')).toBeNull()
    expect(host.querySelector('[data-testid="recap-co2"]')).toBeNull()
  })

  it('lässt Kosten weg, wo der Preis fehlt', () => {
    const host = mount(recap({ ...facts, ac: { kwh: 71, eur: null }, dc: null }))
    expect(all(host, 'recap-split')).toEqual(['Normal (AC) 71 kWh'])
  })
})

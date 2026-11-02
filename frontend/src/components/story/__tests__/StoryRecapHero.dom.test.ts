// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryRecapHero from '../StoryRecapHero.vue'
import type { Moment, RecapFacts } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const NOW = new Date(2026, 9, 2, 12).getTime()

const facts: RecapFacts = { kwh: 96, co2Kg: 45.4 }
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
  it('zeigt Satz mit Ersparnis in Euro und CO₂, darunter €/100 km, Energie und Ladungen, ohne Normal- und Schnellladen', () => {
    const host = mount(recap(facts))
    expect(text(host)).toContain('September: 512 km für 61 €.')
    expect(text(host)).toContain('Gegenüber einem Verbrenner: 6 € und 45 kg CO₂ gespart.')
    expect(all(host, 'recap-fact')).toEqual(['11,91 € pro 100 km', '96 kWh Geladen', '9 Ladungen'])
    expect(text(host)).not.toMatch(/Normal \(AC\)|Schnell \(DC\)|Benziner/)
    expect(host.querySelector('[data-testid="recap-co2"]')).toBeNull()
  })

  it('ohne Euro-Ersparnis nennt der Satz nur das CO₂, ohne nennenswertes CO₂ nur die Euro', () => {
    expect(text(mount({ ...recap(facts), savingEur: null } as Moment))).toContain('Gegenüber einem Verbrenner: 45 kg CO₂ gespart.')
    app?.unmount(); document.body.innerHTML = ''
    expect(text(mount(recap({ ...facts, co2Kg: 0.4 })))).toContain('Gegenüber einem Verbrenner: 6 € gespart.')
  })

  it('führt beim Tippen auf die ganze Karte zum Vormonat und bietet kein Teilen an', () => {
    const events: unknown[][] = []
    const host = mount(recap(facts), events)
    ;(host.querySelector('[data-testid="story-recap-hero"]') as HTMLElement).click()
    expect(events).toEqual([['act', 'prev_month']])
    ;(host.querySelector('button') as HTMLElement).click()
    expect(events).toEqual([['act', 'prev_month'], ['act', 'prev_month']])
    expect(text(host)).not.toMatch(/teilen/i)
  })

  it('ohne Zahlen des Vormonats ohne Energie und CO₂', () => {
    const host = mount(recap(null))
    expect(all(host, 'recap-fact')).toEqual(['11,91 € pro 100 km', '9 Ladungen'])
    expect(text(host)).toContain('Gegenüber einem Verbrenner: 6 € gespart.')
  })

  it('ohne Ersparnis und ohne Strecke keine leeren Kacheln', () => {
    const host = mount({ ...recap(facts), savingEur: null, km: 0 } as Moment)
    expect(all(host, 'recap-fact')).toEqual(['96 kWh Geladen', '9 Ladungen'])
  })

  it('setzt den Ausblick direkt unter die Zahlen', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp({ render: () => h(StoryRecapHero, { moment: recap(facts), carId: 'c1', carName: 'VW ID.3' }, { outlook: () => h('p', { 'data-testid': 'outlook' }, 'Ausblick') }) })
    app.use(createPinia()).use(i18n)
    app.mount(host)
    const factsList = host.querySelector('[data-testid="recap-fact"]')!.parentElement!
    expect(factsList.nextElementSibling?.getAttribute('data-testid')).toBe('outlook')
  })
})

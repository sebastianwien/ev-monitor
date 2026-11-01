// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryNewsCard from '../StoryNewsCard.vue'
import type { Moment } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const NOW = new Date(2026, 9, 24, 12).getTime()
const base = { at: NOW, lifetimeMs: Infinity, actionable: false, dismissible: false }

function mount(moment: Moment, events: unknown[][] = [], extra: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryNewsCard, { moment, carId: 'c1', carName: 'Tesla Model 3', now: NOW, onAct: (id: string) => events.push(['act', id]), onDismiss: () => events.push(['dismiss']), ...extra })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('StoryNewsCard', () => {
  it('Ansehen ist ein grüner Link mit Pfeil, × meldet erledigt', () => {
    const events: unknown[][] = []
    const host = mount({ ...base, kind: 'first_frost', key: 'f', tempC: 3, winterKm: 330, summerKm: 430 }, events)
    const link = host.querySelector('button.text-green-700')!
    expect(text(link)).toBe('Winterwerte ansehen')
    expect(link.querySelector('svg')).not.toBeNull()
    expect(host.querySelector('.bg-green-600')).toBeNull()
    ;(link as HTMLButtonElement).click()
    host.querySelector<HTMLElement>('[data-testid="story-news-dismiss"]')!.click()
    expect(events).toEqual([['act', 'winter'], ['dismiss']])
  })

  it('Tun ist ein Knopf in ganzer Breite: Preis nachtragen führt zur Ladeliste', () => {
    const host = mount({ ...base, kind: 'gap', key: 'g', actionable: true, logId: 'l1', count: 1, kwh: 28.5, since: NOW - 86_400_000 })
    const button = host.querySelector('a.bg-green-600')!
    expect(text(button)).toBe('Preis nachtragen')
    expect(button.className).toContain('w-full')
    expect(button.getAttribute('href')).toContain('nachtragen=preis')
    expect(text(host)).toContain('Weiß ich nicht mehr')
  })

  it('Meilenstein: Teilen als Symbol oben rechts, kein Knopf', () => {
    const events: unknown[][] = []
    const host = mount({ ...base, kind: 'milestone', key: 'm', metric: 'km', value: 20000, charges: 400, km: 20000, co2Kg: 2400 }, events)
    host.querySelector<HTMLElement>('[data-testid="story-news-share"]')!.click()
    expect(events).toEqual([['act', 'share']])
    expect(host.querySelector('.bg-green-600')).toBeNull()
  })

  it('Rückblick: Satz des Vormonats, Ersparnis gegenüber dem Verbrenner darunter, "Rückblick teilen" als Knopf', () => {
    const host = mount({ ...base, kind: 'month_change', key: 'mc', year: 2026, month: 9, km: 512, eur: 61, charges: 9, savingEur: 6.4, places: [], peer: null })
    expect(text(host)).toContain('September: 512 km für 61 €.')
    expect(host.querySelector('[data-testid="story-news-details"]')).toBeNull()
    expect(text(host)).toContain('Gegenüber einem Verbrenner: 6 € gespart.')
    expect(text(host.querySelector('button.bg-green-600'))).toBe('Rückblick teilen')
  })

  it('Rückblick mit Plätzen und Modellvergleich: Zusatzzeilen mit Symbol, "September ansehen" als leiser Knopf', () => {
    const events: unknown[][] = []
    const host = mount({
      ...base, kind: 'month_change', key: 'mc', year: 2026, month: 9, km: 512, eur: 61, charges: 9, savingEur: 6.4,
      places: [{ category: 'MONTHLY_DISTANCE', rank: 1, value: 1234, unit: 'km' }, { category: 'MONTHLY_KWH', rank: 3, value: 310, unit: 'kWh' }],
      peer: { kind: 'consumption', mine: 15.2, peers: 16.8 },
    }, events)
    const details = [...host.querySelectorAll('[data-testid="story-news-details"] li')].map(text)
    expect(details).toHaveLength(2)
    expect(details[0]).toMatch(/^Platz 1 bei .+ · Platz 3 bei .+/)
    expect(details[1]).toBe('Du: 15,2 kWh/100 km, andere Fahrer deines Modells im September: 16,8.')
    expect(host.querySelectorAll('[data-testid="story-news-details"] svg')).toHaveLength(2)
    const view = [...host.querySelectorAll('button')].find(b => text(b) === 'September ansehen')!
    view.click()
    expect(events).toEqual([['act', 'prev_month']])
  })

  it('Rückblick ohne Platz in den Top 10: Euro-Vergleich als Zusatzzeile, teurer ist eine Auskunft', () => {
    const host = mount({ ...base, kind: 'month_change', key: 'mc', year: 2026, month: 9, km: 512, eur: 61, charges: 9, savingEur: null, places: [], peer: { kind: 'cost', diffEur: 12.4 } })
    const details = [...host.querySelectorAll('[data-testid="story-news-details"] li')].map(text)
    expect(details).toEqual(['12 € mehr für Strom als andere Fahrer deines Modells im September.'])
  })

  it('Meilenstein mit Supporter-Bitte: leiser Link zum Pack mit Herkunft, ohne Bitte keiner', () => {
    const ms = { ...base, kind: 'milestone' as const, key: 'm', metric: 'km' as const, value: 20000, charges: 400, km: 20000, co2Kg: 2400 }
    let host = mount({ ...ms, support: true })
    const link = [...host.querySelectorAll('a')].find(a => a.getAttribute('href')?.startsWith('/supporter'))
    expect(link?.getAttribute('href')).toBe('/supporter?from=story_milestone')
    expect(text(link!)).toBe('Projekt unterstützen')
    app?.unmount(); document.body.innerHTML = ''
    host = mount(ms)
    expect(host.querySelector('a[href^="/supporter"]')).toBeNull()
  })

  it('ohne Erledigt: kein × und kein "Alles erfasst", wenn die Karte als Kopf steht', () => {
    const host = mount({ ...base, kind: 'first_frost', key: 'f', tempC: 3, winterKm: 330, summerKm: 430 }, [], { dismissable: false })
    expect(host.querySelector('[data-testid="story-news-dismiss"]')).toBeNull()
  })
})

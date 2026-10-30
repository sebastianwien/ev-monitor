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

function mount(moment: Moment, events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryNewsCard, { moment, carId: 'c1', carName: 'Tesla Model 3', now: NOW, onAct: (id: string) => events.push(['act', id]), onDismiss: () => events.push(['dismiss']) })
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

  it('Rückblick: Satz des Vormonats, Benzin darunter, "Rückblick teilen" als Knopf', () => {
    const host = mount({ ...base, kind: 'month_change', key: 'mc', year: 2026, month: 9, km: 512, eur: 61, charges: 9, petrolEur: 67 })
    expect(text(host)).toContain('September: 512 km für 61 €.')
    expect(text(host)).toContain('Mit Benzin wären es 67 €.')
    expect(text(host.querySelector('button.bg-green-600'))).toBe('Rückblick teilen')
  })

  it('Ranglisten-Abschluss: alle Treppchenplätze, Link zur Rangliste', () => {
    const host = mount({ ...base, kind: 'rank', key: 'r', year: 2026, month: 9, places: [{ category: 'MONTHLY_DISTANCE', rank: 1, value: 1234, unit: 'km' }, { category: 'MONTHLY_KWH', rank: 3, value: 310, unit: 'kWh' }] })
    expect(text(host)).toContain('Im September auf dem Treppchen.')
    expect(text(host)).toMatch(/Platz 1 bei .+ · Platz 3 bei .+/)
    expect(host.querySelector('a.text-green-700')!.getAttribute('href')).toBe('/leaderboard')
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
})

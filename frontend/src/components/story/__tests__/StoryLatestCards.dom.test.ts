// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryLatestCards from '../StoryLatestCards.vue'
import type { StoryLatest } from '../../../composables/storyInsights'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function mount(latest: StoryLatest, now: number, extra: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryLatestCards, { latest, carId: 'c1', now, ...extra })
  app.use(createPinia()).use(i18n)
  app.component('router-link', RouterLink)
  app.mount(host)
  return host
}

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('StoryLatestCards', () => {
  const now = Date.parse('2026-10-02T12:00:00Z')

  it('zwei tippbare Karten: Ladung mit kWh und Preis, Fahrt mit km, beide ins Logbuch', () => {
    const host = mount({
      charge: { id: 'l', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4 },
      trip: { id: 't', at: '2026-10-02T09:00:00Z', km: 23.4 },
    }, now)
    const charge = host.querySelector('[data-testid="story-latest-charge"]')
    expect(text(charge)).toContain('Geladen: 31,9 kWh')
    expect(text(charge)).toContain('vor 18 Stunden')
    expect(text(charge)).toContain('12,40 €')
    expect(charge?.getAttribute('href')).toBe('/logs')
    const trip = host.querySelector('[data-testid="story-latest-trip"]')
    expect(text(trip)).toContain('Gefahren: 23 km')
    expect(text(trip)).toContain('vor 3 Stunden')
    expect(trip?.getAttribute('href')).toBe('/logs')
  })

  it('ohne Preis führt die Ladung direkt zum Nachtragen', () => {
    const host = mount({ charge: { id: 'l', at: '2026-10-02T11:59:30Z', kwh: 10, costEur: null }, trip: null }, now)
    const charge = host.querySelector('[data-testid="story-latest-charge"]')
    expect(text(charge)).toContain('gerade eben')
    expect(text(charge)).toContain('Preis nachtragen')
    expect(charge?.getAttribute('href')).toBe('/logs?car=c1&nachtragen=preis')
    expect(host.querySelector('[data-testid="story-latest-trip"]')).toBeNull()
  })

  it('ohne doppelten Knopf, wenn der Hero schon zum Nachtragen dieser Ladung führt', () => {
    const host = mount({ charge: { id: 'l', at: '2026-10-02T11:59:30Z', kwh: 10, costEur: null }, trip: null }, now, { priceInHero: true })
    expect(text(host.querySelector('[data-testid="story-latest-charge"]'))).not.toContain('Preis nachtragen')
  })
})

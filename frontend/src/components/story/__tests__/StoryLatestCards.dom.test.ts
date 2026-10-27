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

function mount(latest: StoryLatest, now: number, extra: Record<string, unknown> = {}, events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryLatestCards, {
    latest, now, ...extra,
    onOpenCharge: (...a: unknown[]) => events.push(['charge', ...a]),
    onOpenTrip: (...a: unknown[]) => events.push(['trip', ...a]),
  })
  app.use(createPinia()).use(i18n)
  app.component('router-link', RouterLink)
  app.mount(host)
  return host
}

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
const noSoc = { socFrom: null, socTo: null, rangeKm: null }

describe('StoryLatestCards', () => {
  const now = Date.parse('2026-10-02T12:00:00Z')

  it('zwei tippbare Karten: Ladung mit kWh und Preis, Fahrt mit km, beide öffnen ihren Editor', () => {
    const events: unknown[][] = []
    const host = mount({
      charge: { id: 'l', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4, ...noSoc },
      trip: { id: 't', at: '2026-10-02T09:00:00Z', km: 23.4 },
    }, now, {}, events)
    const charge = host.querySelector<HTMLElement>('[data-testid="story-latest-charge"]')
    expect(text(charge)).toContain('Zuletzt geladen: 31,9 kWh')
    expect(text(charge)).toContain('vor 18 Stunden')
    expect(text(charge)).toContain('12,40 €')
    const trip = host.querySelector<HTMLElement>('[data-testid="story-latest-trip"]')
    expect(text(trip)).toContain('Letzte Fahrt: 23 km')
    expect(text(trip)).toContain('vor 3 Stunden')
    charge?.click()
    trip?.click()
    expect(events).toEqual([['charge', 'l', false], ['trip', 't']])
  })

  it('Unterzeile mit Ladeort, Ladeart und Streckentyp, wenn bekannt', () => {
    const host = mount({
      charge: { id: 'l', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4, cpoName: 'Tesla', chargingType: 'AC', ...noSoc },
      trip: { id: 't', at: '2026-10-02T09:00:00Z', km: 2, routeType: 'CITY' },
    }, now)
    expect(text(host.querySelector('[data-testid="story-latest-charge"]'))).toContain('vor 18 Stunden · Tesla, AC · 12,40 €')
    expect(text(host.querySelector('[data-testid="story-latest-trip"]'))).toContain('vor 3 Stunden · Stadt')
  })

  it('ältere Ladung ohne Preis trägt den Chip und führt direkt zum Nachtragen', () => {
    const events: unknown[][] = []
    const host = mount({ charge: { id: 'l', at: '2026-09-30T11:59:30Z', kwh: 10, costEur: null, ...noSoc }, trip: null }, now, {}, events)
    const charge = host.querySelector<HTMLElement>('[data-testid="story-latest-charge"]')
    expect(text(charge)).toContain('vorgestern')
    expect(text(charge)).toContain('Preis nachtragen')
    charge?.click()
    expect(events).toEqual([['charge', 'l', true]])
    expect(host.querySelector('[data-testid="story-latest-trip"]')).toBeNull()
    expect(host.querySelector('[data-testid="story-latest-today"]')).toBeNull()
  })

  it('heute geladen: hervorgehoben mit "Neu", Kosten, Akku und Reichweite, kWh ab Ladestand und Zeit', () => {
    const host = mount({ charge: { id: 'l', at: '2026-10-02T11:59:30Z', kwh: 25.9, costEur: 8.55, chargingType: 'AC', socFrom: 35, socTo: 80, rangeKm: 160 }, trip: null }, now)
    const card = host.querySelector<HTMLElement>('[data-testid="story-latest-today"]')
    expect(card).not.toBeNull()
    expect(text(card)).toContain('Heute geladen · 8,55 €')
    expect(text(card)).toContain('Neu')
    expect(text(card)).toContain('Akku jetzt 80 %, reicht für etwa 160 km')
    expect(text(card)).toContain('25,9 kWh ab 35 % · gerade eben · AC')
    expect(host.querySelector('[data-testid="story-latest-add-price"]')).toBeNull()
    expect(text(card)).not.toContain('Preis nachtragen')
  })

  it('heute geladen ohne Preis: Knopf "Preis eintragen" in der Karte, kein Amber-Chip', () => {
    const events: unknown[][] = []
    const host = mount({ charge: { id: 'l', at: '2026-10-02T09:00:00Z', kwh: 25.9, costEur: null, socFrom: 35, socTo: 80, rangeKm: null }, trip: null }, now, {}, events)
    const card = host.querySelector<HTMLElement>('[data-testid="story-latest-today"]')
    expect(text(card)).toContain('Heute geladen')
    expect(text(card)).toContain('Akku jetzt 80 %')
    expect(text(card)).not.toContain('Preis nachtragen')
    const button = host.querySelector<HTMLElement>('[data-testid="story-latest-add-price"]')!
    expect(text(button)).toBe('Preis eintragen')
    button.click()
    expect(events).toEqual([['charge', 'l', true]])
  })

  it('heute ist der Kalendertag in Ortszeit, nicht 24 Stunden', () => {
    // 2026-10-02 12:00 UTC ist 14:00 in Berlin; 01:30 Ortszeit desselben Tags ist heute, 23:30 des Vortags nicht.
    const todayEarly = mount({ charge: { id: 'l', at: '2026-10-01T23:30:00Z', kwh: 10, costEur: 3, ...noSoc }, trip: null }, now)
    expect(todayEarly.querySelector('[data-testid="story-latest-today"]')).not.toBeNull()
    app!.unmount(); app = null
    const yesterday = mount({ charge: { id: 'l', at: '2026-10-01T21:30:00Z', kwh: 10, costEur: 3, ...noSoc }, trip: null }, now)
    expect(yesterday.querySelector('[data-testid="story-latest-today"]')).toBeNull()
  })
})

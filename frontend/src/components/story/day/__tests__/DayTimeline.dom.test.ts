// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import DayTimeline from '../DayTimeline.vue'
import { eventsOfDay, type DayTrip } from '../../../../composables/storyDay'

// Leaflet läuft nicht in jsdom: die Karte ist hier ein Platzhalter, der zeigt, was sie bekommt
vi.mock('../../../dashboard/ActivityLocationMap.vue', () => ({
  __esModule: true,
  default: { props: ['variant', 'startGeohash', 'endGeohash'], template: '<i data-testid="map-stub" :data-variant="variant" :data-start="startGeohash" />' },
}))

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const at = (h: number, m = 0) => new Date(2026, 7, 16, h, m).toISOString()
const trip = (over: Partial<DayTrip> = {}): DayTrip => ({
  id: 't1', tripStartedAt: at(9), tripEndedAt: at(10), distanceKm: 42, socStart: 80, socEnd: 70, ...over,
})

async function mount(trips: DayTrip[]) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(DayTimeline, { events: eventsOfDay('2026-08-16', trips, []), selectedId: null, merging: false })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  // Die Karte kommt als asynchrone Komponente
  for (let i = 0; i < 5; i++) { await new Promise(r => setTimeout(r)); await nextTick() }
  return host
}

describe('DayTimeline Minikarte', () => {
  it('Fahrt mit Ort: Vorschaukarte, auch wenn Start gleich Ziel', async () => {
    const host = await mount([trip({ locationStartGeohash: 'u33df5x8', locationEndGeohash: 'u33df5x8' })])
    const map = host.querySelector('[data-testid="day-trip-thumb"] [data-testid="map-stub"]')
    expect(map?.getAttribute('data-variant')).toBe('thumb')
    expect(map?.getAttribute('data-start')).toBe('u33df5x8')
  })

  it('Fahrt ohne Ort: keine Karte, kein Kartenhinweis', async () => {
    const host = await mount([trip()])
    expect(host.querySelector('[data-testid="day-trip-thumb"]')).toBeNull()
    expect(host.querySelector('[data-testid="day-map-credit"]')).toBeNull()
  })

  it('Kartenhinweis einmal unter der Liste, mit Router-Nennung bei berechneter Strecke', async () => {
    const host = await mount([
      trip({ locationStartGeohash: 'u33df5ze', locationEndGeohash: 'u33dc0bc' }),
      trip({ id: 't2', tripStartedAt: at(12), tripEndedAt: at(13), locationStartGeohash: 'u33dc0bc', locationEndGeohash: 'u33df5ze', routePolyline: '_p~iF~ps|U_ulLnnqC', routeKind: 'MATCHED' }),
    ])
    const credits = host.querySelectorAll('[data-testid="day-map-credit"]')
    expect(credits).toHaveLength(1)
    expect(credits[0].textContent).toBe('© OpenStreetMap · Route © openrouteservice by HeiGIT')
  })

  it('ohne berechnete Strecke nur OpenStreetMap', async () => {
    const host = await mount([trip({ locationStartGeohash: 'u33df5ze', locationEndGeohash: 'u33dc0bc' })])
    expect(host.querySelector('[data-testid="day-map-credit"]')?.textContent).toBe('© OpenStreetMap')
  })
})

async function mountWith(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(DayTimeline, { selectedId: null, merging: false, ...props })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
  return host
}
const rowsOf = (host: HTMLElement) => [...host.querySelectorAll('[data-testid="day-timeline"] > li')]
  .map(li => li.getAttribute('data-testid') ?? li.querySelector('button')?.getAttribute('data-testid'))

describe('DayTimeline Story und Logbuch', () => {
  const charge = { id: 'l1', loggedAt: at(13), chargeDurationMinutes: 30, socBeforeChargePercent: 40, socAfterChargePercent: 80,
    kwhCharged: 20, kwhAtVehicle: null, costEur: 6, isPublicCharging: false, cpoName: null, chargingType: 'AC' as const }
  const day = () => eventsOfDay('2026-08-16', [trip(), trip({ id: 't2', tripStartedAt: at(17), tripEndedAt: at(17, 20) })], [charge])

  it('Story: Standzeit aus dem Tag, zeitlich aufsteigend', async () => {
    const host = await mountWith({ events: day() })
    expect(rowsOf(host)).toEqual(['day-row-trip', 'day-standing', 'day-row-charge', 'day-standing', 'day-row-trip'])
  })

  it('Logbuch: neueste zuerst, Standzeit über Nacht unten', async () => {
    const host = await mountWith({
      events: day(), newestFirst: true,
      standing: { t1: { minutes: 600, kwh: 0.24 }, t2: { minutes: 210, kwh: null } },
    })
    expect(rowsOf(host)).toEqual(['day-row-trip', 'day-standing', 'day-row-charge', 'day-row-trip', 'day-standing'])
    const standings = host.querySelectorAll('[data-testid="day-standing"]')
    expect(standings[0].textContent).not.toContain('kWh')
    expect(standings[1].textContent).toContain('0,24 kWh')
    // Mit Standverlust kurz, damit die Zeile auf dem Handy nicht umbricht: "10 h · 0,24 kWh Standverlust"
    expect(standings[1].textContent).toMatch(/^\s*10 h\s*·/)
    expect(standings[0].textContent).toContain('gestanden')
  })
})

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

// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import RecentActivityCard from '../RecentActivityCard.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const charge = { id: 'c1', kwhCharged: 50, costEur: 22.02, loggedAt: '2026-10-28T20:00:00Z' }
const trip = { id: 't1', distanceKm: 12, endedAt: '2026-10-28T21:00:00Z' }

function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(RecentActivityCard, { effectiveBatteryCapacityKwh: 60, sourceInfo: () => null, ...props })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

describe('RecentActivityCard', () => {
  it('zeigt Ladung und Fahrt', () => {
    const host = mount({ charge, trip })
    expect(host.querySelector('[data-testid="recent-charge-tile"]')).not.toBeNull()
    expect(host.querySelector('[data-testid="recent-trip-tile"]')).not.toBeNull()
  })

  it('blendet die letzte Ladung aus, solange live geladen wird, die Fahrt bleibt', () => {
    const host = mount({ charge, trip, hideCharge: true })
    expect(host.querySelector('[data-testid="recent-charge-tile"]')).toBeNull()
    expect(host.querySelector('[data-testid="recent-trip-tile"]')).not.toBeNull()
  })

  it('zeigt die Fahrt auch ohne Ladung', () => {
    const host = mount({ charge: null, trip })
    expect(host.querySelector('[data-testid="recent-trip-tile"]')).not.toBeNull()
  })
})

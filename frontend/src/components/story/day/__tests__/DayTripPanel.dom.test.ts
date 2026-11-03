// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'

vi.mock('../../../../api/axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: null })), post: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
import DayTripPanel from '../DayTripPanel.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const trip = (dataSource: string) => ({
  id: 't1', tripStartedAt: '2026-11-02T07:37:00Z', tripEndedAt: '2026-11-02T07:52:00Z', distanceKm: 3.8, socStart: 77, socEnd: 75, dataSource,
})

async function mount(dataSource: string) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(DayTripPanel, { trip: trip(dataSource), carId: 'car-1', newEnd: new Date() }) })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
}

describe('DayTripPanel', () => {
  // Jede Fahrt-Ansicht (Tagesblatt, Logbuch, Letzte Fahrt) fragt gleich, nicht nur das Logbuch
  it('erkannte Fahrt: "Korrekt erkannt?" steht in der Ansicht selbst', async () => {
    await mount('TESLA_FLEET_TELEMETRY')
    expect(document.querySelector('[data-testid="trip-feedback"]')).not.toBeNull()
  })

  it('selbst angelegte Fahrt: keine Frage', async () => {
    await mount('USER_CREATED')
    expect(document.querySelector('[data-testid="trip-feedback"]')).toBeNull()
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'

vi.mock('../../../../api/axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: null })), post: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
import api from '../../../../api/axios'
import DayTripPanel from '../DayTripPanel.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const trip = (dataSource: string, measured = false) => ({
  id: 't1', tripStartedAt: '2026-11-02T07:37:00Z', tripEndedAt: '2026-11-02T07:52:00Z', distanceKm: 3.8, socStart: 77, socEnd: 75, dataSource, measured,
})

async function mount(dataSource: string, measured = false) {
  const changed: number[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(DayTripPanel, { trip: trip(dataSource, measured), carId: 'car-1', newEnd: new Date(), onChanged: () => changed.push(1) }) })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
  return changed
}
const chip = (label: string) => Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === label)!
const flush = () => new Promise(r => setTimeout(r, 0))

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

  describe('gemessene Fahrt', () => {
    it('kein Speichern-Knopf, ein Tipp speichert sofort', async () => {
      vi.mocked(api.patch).mockResolvedValueOnce({ data: {} })
      const changed = await mount('TESLA_FLEET_TELEMETRY', true)
      expect(document.querySelector('[data-testid="day-trip-save"]')).toBeNull()
      chip('Autobahn').click()
      await flush()
      expect(api.patch).toHaveBeenCalledWith('/trips/t1', { routeType: 'HIGHWAY', loadExtras: [] })
      expect(changed).toEqual([1])
    })

    it('Server lehnt ab: der Chip springt zurück', async () => {
      vi.mocked(api.patch).mockRejectedValueOnce(new Error('500'))
      const changed = await mount('TESLA_FLEET_TELEMETRY', true)
      chip('Anhänger').click()
      await flush(); await flush()
      expect(chip('Anhänger').getAttribute('aria-pressed')).not.toBe('true')
      expect(changed).toEqual([])
    })
  })

  it('selbst angelegte Fahrt behält Speichern', async () => {
    await mount('USER_CREATED')
    expect(document.querySelector('[data-testid="day-trip-save"]')).not.toBeNull()
  })
})

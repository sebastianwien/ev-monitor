import { describe, it, expect } from 'vitest'
import { isDayBoundary } from '../tripCalculations'

// Trips einer Fahrtgruppe sind absteigend sortiert (neuester zuerst).
const trips = [
  { tripStartedAt: '2026-08-02T09:56:00', tripEndedAt: '2026-08-02T10:24:00' },
  { tripStartedAt: '2026-08-01T22:55:00', tripEndedAt: '2026-08-01T22:57:00' },
  { tripStartedAt: '2026-08-01T22:36:00', tripEndedAt: '2026-08-01T22:37:00' },
]

describe('isDayBoundary', () => {
  it('nie beim ersten Trip', () => {
    expect(isDayBoundary(trips, 0)).toBe(false)
  })

  it('true wenn der vorherige Trip an einem anderen Tag endete', () => {
    expect(isDayBoundary(trips, 1)).toBe(true)
  })

  it('false innerhalb desselben Tages', () => {
    expect(isDayBoundary(trips, 2)).toBe(false)
  })

  it('nutzt tripStartedAt des Vorgaengers, wenn tripEndedAt fehlt', () => {
    const partial = [{ tripStartedAt: '2026-08-02T09:56:00', tripEndedAt: null }, trips[1]]
    expect(isDayBoundary(partial, 1)).toBe(true)
  })

  it('kein Trenner auf Verdacht, wenn gar kein Zeitstempel da ist', () => {
    const broken = [{ tripStartedAt: null, tripEndedAt: null }, trips[1]]
    expect(isDayBoundary(broken, 1)).toBe(false)
  })

  it('faellt auf tripStartedAt zurueck, wenn tripEndedAt fehlt', () => {
    const partial = [
      { tripStartedAt: '2026-08-02T09:56:00', tripEndedAt: null },
      { tripStartedAt: '2026-08-02T08:00:00', tripEndedAt: '2026-08-02T08:20:00' },
    ]
    expect(isDayBoundary(partial, 1)).toBe(false)
  })
})

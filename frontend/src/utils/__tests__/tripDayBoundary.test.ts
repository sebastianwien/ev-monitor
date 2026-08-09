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

  it('kein Trenner auf Verdacht, wenn gar kein Zeitstempel da ist', () => {
    const broken = [{ tripStartedAt: null, tripEndedAt: null }, trips[1]]
    expect(isDayBoundary(broken, 1)).toBe(false)
  })

  // Ein Trip ueber Mitternacht gehoert zu zwei Tagen. Der Trenner beschriftet immer den Trip
  // darunter mit dessen tripStartedAt - also darf auch nur das Startdatum ueber den Trenner
  // entscheiden, sonst erscheint dasselbe Datum zweimal hintereinander.
  it('kein zweiter Trenner mit gleichem Datum bei einem Trip ueber Mitternacht', () => {
    const overMidnight = [
      { tripStartedAt: '2026-08-03T09:26:00', tripEndedAt: '2026-08-03T15:07:00' },
      { tripStartedAt: '2026-08-02T20:33:00', tripEndedAt: '2026-08-03T09:26:00' },
      { tripStartedAt: '2026-08-02T18:20:00', tripEndedAt: '2026-08-02T20:33:00' },
    ]
    expect(isDayBoundary(overMidnight, 1)).toBe(true)   // Wechsel 3.8. -> 2.8.
    expect(isDayBoundary(overMidnight, 2)).toBe(false)  // beide starten am 2.8.
  })

  it('kommt ohne tripEndedAt aus', () => {
    const partial = [
      { tripStartedAt: '2026-08-02T09:56:00', tripEndedAt: null },
      { tripStartedAt: '2026-08-02T08:00:00', tripEndedAt: null },
      { tripStartedAt: '2026-08-01T22:00:00', tripEndedAt: null },
    ]
    expect(isDayBoundary(partial, 1)).toBe(false)
    expect(isDayBoundary(partial, 2)).toBe(true)
  })
})

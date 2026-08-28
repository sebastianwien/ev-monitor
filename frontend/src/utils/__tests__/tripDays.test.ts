import { describe, it, expect } from 'vitest'
import { groupTripsByDay } from '../tripCalculations'

/** Trips einer Fahrtgruppe sind absteigend sortiert (neuester zuerst). */
const trips = [
  { id: 'a', tripStartedAt: '2026-08-26T07:37:00', tripEndedAt: '2026-08-26T07:48:00', distanceKm: 3.5 },
  { id: 'b', tripStartedAt: '2026-08-25T19:19:00', tripEndedAt: '2026-08-25T19:34:00', distanceKm: 4.4 },
  { id: 'c', tripStartedAt: '2026-08-25T07:32:00', tripEndedAt: '2026-08-25T07:44:00', distanceKm: 3.8 },
]

describe('groupTripsByDay', () => {
  it('schneidet die Gruppe in Tage, in der Reihenfolge der Liste', () => {
    const days = groupTripsByDay(trips)

    expect(days.map((d) => d.trips.length)).toEqual([1, 2])
    expect(days[0].trips[0].id).toBe('a')
    expect(days[1].trips.map((t: any) => t.id)).toEqual(['b', 'c'])
  })

  it('zaehlt Fahrten und Kilometer je Tag', () => {
    const days = groupTripsByDay(trips)

    expect(days[1].tripCount).toBe(2)
    expect(days[1].km).toBeCloseTo(8.2, 5)
  })

  it('gibt jedem Tag einen stabilen Schluessel aus seinem Datum', () => {
    const days = groupTripsByDay(trips)

    expect(days[0].dateKey).toBe('2026-08-26')
    expect(days[1].dateKey).toBe('2026-08-25')
  })

  it('ordnet eine Fahrt ueber Mitternacht ihrem Starttag zu', () => {
    // Sonst eroeffnete die Ankunft einen Tag, an dem niemand losgefahren ist.
    const days = groupTripsByDay([
      { id: 'x', tripStartedAt: '2026-08-20T23:40:00', tripEndedAt: '2026-08-21T00:15:00', distanceKm: 12 },
    ])

    expect(days).toHaveLength(1)
    expect(days[0].dateKey).toBe('2026-08-20')
  })

  it('kommt mit einer leeren Gruppe klar', () => {
    expect(groupTripsByDay([])).toEqual([])
  })

  it('laesst Fahrten ohne Startzeit nicht verschwinden', () => {
    const days = groupTripsByDay([
      { id: 'a', tripStartedAt: '2026-08-26T07:37:00', tripEndedAt: null, distanceKm: 1 },
      { id: 'broken', tripStartedAt: null, tripEndedAt: null, distanceKm: null },
    ])

    expect(days.flatMap((d) => d.trips).map((t: any) => t.id)).toEqual(['a', 'broken'])
  })
})

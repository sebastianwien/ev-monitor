import { describe, it, expect } from 'vitest'
import { summarizeTripMonth, resolveTripWindow, type TripForSummary } from '../tripMonthSummary'

// Two same-day trips with a parked gap in between. Energy counter drops 40 -> 35.5,
// of which the gap (t1 end 38 -> t2 start 37.5) is standby.
const twoTrips: TripForSummary[] = [
  {
    id: 't1', tripStartedAt: '2026-09-01T06:00:00Z', tripEndedAt: '2026-09-01T06:30:00Z',
    distanceKm: 10, socStart: 60, socEnd: 57, estimatedConsumedKwh: 1.5,
    energyRemainingStartKwh: 40, energyRemainingEndKwh: 38, outsideTempCelsius: 15,
  },
  {
    id: 't2', tripStartedAt: '2026-09-01T12:00:00Z', tripEndedAt: '2026-09-01T12:30:00Z',
    distanceKm: 10, socStart: 55, socEnd: 52, estimatedConsumedKwh: 1.5,
    energyRemainingStartKwh: 37.5, energyRemainingEndKwh: 35.5, outsideTempCelsius: 20,
  },
]

describe('summarizeTripMonth', () => {
  it('returns null for no trips', () => {
    expect(summarizeTripMonth([], 60)).toBeNull()
    expect(summarizeTripMonth(null as unknown as TripForSummary[], 60)).toBeNull()
  })

  it('aggregates distance, drivetrain energy and consumption', () => {
    const s = summarizeTripMonth(twoTrips, 60)!
    expect(s.tripCount).toBe(2)
    expect(s.totalDistanceKm).toBe(20)
    expect(s.drivetrainKwh).toBeCloseTo(3.0, 5)
    expect(s.consumptionKwhPer100km).toBeCloseTo(15.0, 5)
  })

  it('derives standby from the energy gap between consecutive trips', () => {
    const s = summarizeTripMonth(twoTrips, 60)!
    // gap = t1.end(38) - t2.start(37.5) = 0.5 kWh parked drain
    expect(s.standbyKwh).toBeCloseTo(0.5, 5)
  })

  it('sorts unordered input before computing gaps', () => {
    const reversed = [twoTrips[1], twoTrips[0]]
    const s = summarizeTripMonth(reversed, 60)!
    expect(s.standbyKwh).toBeCloseTo(0.5, 5)
    expect(s.totalDistanceKm).toBe(20)
  })

  it('returns null standby when energy fields are missing', () => {
    const noEnergy = twoTrips.map(t => ({ ...t, energyRemainingStartKwh: null, energyRemainingEndKwh: null }))
    const s = summarizeTripMonth(noEnergy, 60)!
    expect(s.standbyKwh).toBeNull()
    // drivetrain still available from estimatedConsumedKwh
    expect(s.drivetrainKwh).toBeCloseTo(3.0, 5)
  })

  it('returns null consumption when there is no distance', () => {
    const zeroDist = twoTrips.map(t => ({ ...t, distanceKm: 0 }))
    const s = summarizeTripMonth(zeroDist, 60)!
    expect(s.totalDistanceKm).toBe(0)
    expect(s.consumptionKwhPer100km).toBeNull()
  })

  it('reports the temperature range and active days', () => {
    const s = summarizeTripMonth(twoTrips, 60)!
    expect(s.tempMin).toBe(15)
    expect(s.tempMax).toBe(20)
    expect(s.activeDays).toBe(1)
  })

  it('buckets distance per calendar day for the strip', () => {
    const acrossDays: TripForSummary[] = [
      { ...twoTrips[0], id: 'a', tripStartedAt: '2026-09-01T06:00:00Z', distanceKm: 12 },
      { ...twoTrips[1], id: 'b', tripStartedAt: '2026-09-03T06:00:00Z', distanceKm: 8 },
    ]
    const s = summarizeTripMonth(acrossDays, 60)!
    expect(s.perDay).toEqual([
      { dateKey: '2026-09-01', km: 12, trips: 1 },
      { dateKey: '2026-09-03', km: 8, trips: 1 },
    ])
    expect(s.activeDays).toBe(2)
  })
})

describe('resolveTripWindow', () => {
  const now = new Date('2026-09-04T10:00:00Z')

  it('THIS_MONTH spans the first of the month to now', () => {
    const w = resolveTripWindow('THIS_MONTH', null, null, now)!
    expect(new Date(w.startMs).toISOString().slice(0, 10)).toBe('2026-09-01')
    expect(w.endMs).toBe(now.getTime())
  })

  it('LAST_MONTH spans the previous whole calendar month', () => {
    const w = resolveTripWindow('LAST_MONTH', null, null, now)!
    expect(new Date(w.startMs).toISOString().slice(0, 7)).toBe('2026-08')
    expect(new Date(w.endMs).toISOString().slice(0, 7)).toBe('2026-08')
  })

  it('LAST_3_MONTHS reaches back two months from the current month start', () => {
    const w = resolveTripWindow('LAST_3_MONTHS', null, null, now)!
    expect(new Date(w.startMs).toISOString().slice(0, 7)).toBe('2026-07')
  })

  it('ALL_TIME is unbounded', () => {
    expect(resolveTripWindow('ALL_TIME', null, null, now)).toBeNull()
  })

  it('CUSTOM uses the supplied bounds', () => {
    const w = resolveTripWindow('CUSTOM', '2026-09-01', '2026-09-02', now)!
    expect(new Date(w.startMs).toISOString().slice(0, 10)).toBe('2026-09-01')
  })
})

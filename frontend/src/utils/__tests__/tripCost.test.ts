import { describe, it, expect } from 'vitest'
import { tripCostPer100km, tripFacts } from '../tripCalculations'

// € pro 100 km einer Fahrt: Verbrauch der Fahrt mal Preis je kWh (Ladung davor bzw. eigener Schnitt).
const trip = { id: 't', tripStartedAt: null, tripEndedAt: null, distanceKm: 10, socStart: null, socEnd: null, estimatedConsumedKwh: 1.8 }

describe('tripCostPer100km', () => {
  it('Verbrauch mal Preis je kWh', () => {
    // 1,8 kWh auf 10 km = 18 kWh/100 km, mal 0,30 €
    expect(tripCostPer100km(trip, 0.3, null)).toBeCloseTo(5.4, 6)
  })

  it('ohne Preis oder ohne Verbrauch nichts', () => {
    expect(tripCostPer100km(trip, null, null)).toBeNull()
    expect(tripCostPer100km({ ...trip, estimatedConsumedKwh: null }, 0.3, null)).toBeNull()
  })
})

describe('tripFacts', () => {
  it('Energie und Kosten der Fahrt, je 100 km erst ab 1 km', () => {
    expect(tripFacts(trip, 0.3, null)).toEqual({ eur: expect.closeTo(0.54, 6), eurPer100km: expect.closeTo(5.4, 6), kwh: expect.closeTo(1.8, 6), kwhPer100km: expect.closeTo(18, 6), estimated: false })
    const short = tripFacts({ ...trip, distanceKm: 0.6, estimatedConsumedKwh: 0.12 }, 0.3, null)
    expect(short.eurPer100km).toBeNull()
    expect(short.kwhPer100km).toBeNull()
    expect(short.eur).toBeCloseTo(0.036, 6)
  })

  it('ohne Preis keine Kosten, ohne Verbrauch nichts', () => {
    expect(tripFacts(trip, null, null)).toMatchObject({ eur: null, eurPer100km: null, kwhPer100km: expect.closeTo(18, 6) })
    expect(tripFacts({ ...trip, estimatedConsumedKwh: null }, 0.3, null)).toEqual({ eur: null, eurPer100km: null, kwh: null, kwhPer100km: null, estimated: false })
  })
})

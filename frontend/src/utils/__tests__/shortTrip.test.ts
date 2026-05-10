import { describe, expect, it } from 'vitest'
import { SHORT_TRIP_THRESHOLD_KM, isShortTrip } from '../shortTrip'

describe('isShortTrip', () => {
  it('returns false when distance is null', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: null })).toBe(false)
  })

  it('returns false when distance is undefined', () => {
    expect(isShortTrip({})).toBe(false)
  })

  it('returns false when distance is zero', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: 0 })).toBe(false)
  })

  it('returns false when distance is negative (data glitch)', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: -5 })).toBe(false)
  })

  it('returns true for 1 km trip (Zomtecos pattern)', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: 1 })).toBe(true)
  })

  it('returns true for 9 km trip (just below threshold)', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: 9 })).toBe(true)
  })

  it('returns false at the 10 km boundary (excluded)', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: 10 })).toBe(false)
  })

  it('returns false above the threshold', () => {
    expect(isShortTrip({ distanceSinceLastChargeKm: 50 })).toBe(false)
  })

  it('exposes the threshold as a constant for callers', () => {
    expect(SHORT_TRIP_THRESHOLD_KM).toBe(10)
  })
})

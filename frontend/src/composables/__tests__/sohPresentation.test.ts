import { describe, it, expect } from 'vitest'
import { sohAxisBounds, sohEmptyStateKey, sohTrustKey, nominalCapacityKwh } from '../sohPresentation'
import type { BatterySohStatus } from '../../api/carService'

const status = (over: Partial<BatterySohStatus> = {}): BatterySohStatus => ({
  requiredSocHubPercent: 75,
  largestSocHubPercent: null,
  qualifyingChargeCount: 0,
  capacityKnown: true,
  ...over,
})

describe('sohAxisBounds', () => {
  it('uses a fixed band so small differences do not look dramatic', () => {
    // Auto-scaling to [91.9, 92.1] would turn 0.2% of noise into a full-height drop.
    expect(sohAxisBounds([92, 91.9, 92.1])).toEqual({ min: 80, max: 100 })
  })

  it('never exceeds 100 because SoH is capped there', () => {
    expect(sohAxisBounds([100, 99.5]).max).toBe(100)
  })

  it('extends downward when a value falls below the default floor', () => {
    expect(sohAxisBounds([78, 92])).toEqual({ min: 75, max: 100 })
  })

  it('rounds the extended floor down to the next multiple of five', () => {
    expect(sohAxisBounds([61.2]).min).toBe(60)
  })

  it('falls back to the default band when there is no data', () => {
    expect(sohAxisBounds([])).toEqual({ min: 80, max: 100 })
  })
})

describe('sohEmptyStateKey', () => {
  it('reports a missing capacity first - no charge can fix that', () => {
    expect(sohEmptyStateKey(status({ capacityKnown: false, largestSocHubPercent: 90 })))
      .toBe('no_capacity')
  })

  it('reports missing charges when the car has no usable log at all', () => {
    expect(sohEmptyStateKey(status({ largestSocHubPercent: null }))).toBe('no_charges')
  })

  it('reports a too-small hub when charges exist but none is big enough', () => {
    expect(sohEmptyStateKey(status({ largestSocHubPercent: 58 }))).toBe('hub_too_small')
  })

  it('reports pending when qualifying charges exist but no entry was written yet', () => {
    expect(sohEmptyStateKey(status({ largestSocHubPercent: 82, qualifyingChargeCount: 2 })))
      .toBe('pending')
  })
})

describe('nominalCapacityKwh', () => {
  it('reverses the SoH adjustment applied to the effective capacity', () => {
    // 75 kWh nominal at 8% degradation -> 69 kWh effective
    expect(nominalCapacityKwh(69, 8)).toBeCloseTo(75, 5)
  })

  it('returns the effective value unchanged when no degradation is known', () => {
    expect(nominalCapacityKwh(75, null)).toBe(75)
  })

  it('returns null without an effective capacity', () => {
    expect(nominalCapacityKwh(null, 8)).toBeNull()
  })

  it('returns null at 100% degradation instead of dividing by zero', () => {
    expect(nominalCapacityKwh(0, 100)).toBeNull()
  })
})

describe('sohTrustKey', () => {
  it('maps every source to its own wording', () => {
    expect(sohTrustKey('VEHICLE_BMS')).toBe('trust_bms')
    expect(sohTrustKey('CHARGE_LOG')).toBe('trust_estimate')
    expect(sohTrustKey('MANUAL')).toBe('trust_manual')
    expect(sohTrustKey('UNKNOWN')).toBe('trust_unknown')
  })
})

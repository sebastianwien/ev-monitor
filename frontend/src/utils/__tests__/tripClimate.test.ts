import { describe, it, expect } from 'vitest'
import {
  activeClimateLoads,
  climateShare,
  hasActiveClimateLoads,
  climateDurationMinutes,
  type ClimateSummary,
} from '../tripClimate'

const summary = (over: Partial<ClimateSummary> = {}): ClimateSummary => ({
  tripSeconds: 1800,
  comfortHeat: { active: false, seconds: 0 },
  hvacHeating: { active: false, seconds: 0 },
  hvacCooling: { active: false, seconds: 0 },
  batteryHeater: { active: false, seconds: 0 },
  ...over,
})

describe('activeClimateLoads', () => {
  it('returns only active loads in fixed order', () => {
    const c = summary({
      hvacCooling: { active: true, seconds: 600 },
      comfortHeat: { active: true, seconds: 1240 },
    })
    expect(activeClimateLoads(c).map((l) => l.key)).toEqual(['comfortHeat', 'hvacCooling'])
  })

  it('computes the share of the trip per load, clamped to 100', () => {
    const c = summary({
      hvacHeating: { active: true, seconds: 900 }, // 50%
      comfortHeat: { active: true, seconds: 3600 }, // 200% -> clamp 100
    })
    const byKey = Object.fromEntries(activeClimateLoads(c).map((l) => [l.key, l.share]))
    expect(byKey.hvacHeating).toBe(50)
    expect(byKey.comfortHeat).toBe(100)
  })

  it('returns share null when trip duration is unknown', () => {
    const c = summary({ tripSeconds: 0, comfortHeat: { active: true, seconds: 300 } })
    expect(activeClimateLoads(c)[0].share).toBeNull()
  })

  it('returns empty for null climate or all-inactive', () => {
    expect(activeClimateLoads(null)).toEqual([])
    expect(activeClimateLoads(undefined)).toEqual([])
    expect(activeClimateLoads(summary())).toEqual([])
  })
})

describe('hasActiveClimateLoads', () => {
  it('is false without data and true with at least one active load', () => {
    expect(hasActiveClimateLoads(null)).toBe(false)
    expect(hasActiveClimateLoads(summary())).toBe(false)
    expect(hasActiveClimateLoads(summary({ batteryHeater: { active: true, seconds: 60 } }))).toBe(true)
  })
})

describe('climateShare', () => {
  it('guards zero/missing trip duration', () => {
    expect(climateShare(300, 0)).toBeNull()
    expect(climateShare(300, null)).toBeNull()
    expect(climateShare(300, 600)).toBe(50)
  })
})

describe('climateDurationMinutes', () => {
  it('rounds seconds to whole minutes', () => {
    expect(climateDurationMinutes(1240)).toBe(21)
    expect(climateDurationMinutes(90)).toBe(2)
  })
})

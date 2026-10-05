import { describe, it, expect } from 'vitest'
import { formChanges } from '../voiceEdit'
import { emptyLogForm } from '../wizardLogic'

describe('formChanges', () => {
  it('lists changed values with before and after, place first', () => {
    const before = { ...emptyLogForm(), odometerKm: 31207, kwhCharged: 32, isPublicCharging: false }
    const after = { ...before, odometerKm: 31270, isPublicCharging: true, cpoName: 'EnBW' }
    expect(formChanges(before, after).map(c => c.key)).toEqual(['place', 'odometerKm'])
    expect(formChanges(before, after)[1]).toEqual({ key: 'odometerKm', from: 31207, to: 31270 })
  })

  it('ignores float noise and null versus undefined', () => {
    const before = { ...emptyLogForm(), costEur: 16.52 }
    const after = { ...before, costEur: 16.520000001, loggedAt: undefined as unknown as null }
    expect(formChanges(before, after)).toEqual([])
  })
})

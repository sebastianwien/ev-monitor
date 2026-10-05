import { describe, it, expect } from 'vitest'
import { formChanges, undoVoiceChanges } from '../voiceEdit'
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

describe('undoVoiceChanges', () => {
  it('reverts what the voice set and keeps what the user changed afterwards', () => {
    const before = { ...emptyLogForm(), odometerKm: 31207, socAfterChargePercent: 80, costEur: 9 }
    const afterVoice = { ...before, odometerKm: 31270, socAfterChargePercent: 85 }
    const current = { ...afterVoice, socAfterChargePercent: 90, costEur: 11 } // danach von Hand

    const out = undoVoiceChanges(before, afterVoice, current)

    expect(out.odometerKm).toBe(31207) // Sprache - zurück
    expect(out.socAfterChargePercent).toBe(90) // von Hand nach der Sprache - bleibt
    expect(out.costEur).toBe(11) // nie von der Sprache berührt - bleibt
  })

  it('reverts the place as a whole only if untouched since', () => {
    const before = { ...emptyLogForm(), isPublicCharging: true, cpoName: 'EnBW' }
    const afterVoice = { ...before, isPublicCharging: false, cpoName: null, latitude: 49.9, longitude: 10.9 }
    expect(undoVoiceChanges(before, afterVoice, { ...afterVoice })).toMatchObject({ isPublicCharging: true, cpoName: 'EnBW', latitude: null })

    const moved = { ...afterVoice, latitude: 50.1, longitude: 11.0 }
    expect(undoVoiceChanges(before, afterVoice, moved)).toMatchObject({ isPublicCharging: false, latitude: 50.1 })
  })
})

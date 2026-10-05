import { describe, it, expect } from 'vitest'
import { durationBucket, latencyBucket, filledCount, placeOutcome, spokenSnapshot, correctedFields } from '../voiceAnalytics'
import { emptyLogForm, mergeUncertain, type VoiceDraft, type VoiceDraftFields } from '../wizardLogic'

const fields = (over: Partial<VoiceDraftFields> = {}): VoiceDraftFields => ({
  kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: null, costEur: null, pricePerKwh: null,
  loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
  spokenAddress: null, uncertain: [], ...over,
})
const draft = (over: Partial<VoiceDraft> = {}): VoiceDraft => ({
  transcript: 'x', fields: fields(), place: null, chargingProviderId: null,
  usage: { plan: 'paid', limit: 30, remaining: 29, resetsOn: '2026-11-01' }, ...over,
})

describe('durationBucket', () => {
  it('groups recording length, the automatic stop counts as limit', () => {
    expect(durationBucket(4_000)).toBe('<10s')
    expect(durationBucket(10_000)).toBe('10-30s')
    expect(durationBucket(45_000)).toBe('30-60s')
    expect(durationBucket(59_900)).toBe('limit')
    expect(durationBucket(60_000)).toBe('limit')
  })
})

describe('latencyBucket', () => {
  it('groups the wait for the draft', () => {
    expect(latencyBucket(2_000)).toBe('<3s')
    expect(latencyBucket(4_000)).toBe('3-6s')
    expect(latencyBucket(8_000)).toBe('6-10s')
    expect(latencyBucket(12_000)).toBe('>10s')
  })
})

describe('filledCount', () => {
  it('counts spoken values, ignores the uncertain list', () => {
    expect(filledCount(fields({ kwhCharged: 30, socAfter: 80, uncertain: ['socAfter'] }))).toBe(2)
    expect(filledCount(fields({ spokenAddress: 'Lindenweg 4' }))).toBe(1)
    expect(filledCount(fields())).toBe(0)
    // Backend liefert Ort als Index und Art: zählt einmal
    expect(filledCount({ ...fields({ kwhCharged: 30 }), placeIndex: 0, placeKind: 'home' } as VoiceDraftFields)).toBe(2)
  })
})

describe('placeOutcome', () => {
  it('distinguishes a list match, an address, a free operator and nothing', () => {
    expect(placeOutcome(draft({ place: { kind: 'home', station: null, site: null, cpoName: null } }))).toBe('match')
    expect(placeOutcome(draft({ fields: fields({ spokenAddress: 'Lindenweg 4' }) }))).toBe('address')
    expect(placeOutcome(draft({ place: { kind: 'other', station: null, site: null, cpoName: 'Aral' } }))).toBe('other')
    expect(placeOutcome(draft())).toBe('none')
  })
})

describe('correctedFields', () => {
  it('names the spoken fields the user changed before saving, nothing else', () => {
    const form = emptyLogForm()
    form.kwhCharged = 32; form.odometerKm = 31207; form.costEur = 16.52; form.tireType = 'WINTER'
    const snap = spokenSnapshot(form, fields({ kwhCharged: 32, odometerKm: 31207, pricePerKwh: 0.5 }))

    form.odometerKm = 31270 // korrigiert
    form.tireType = 'SUMMER' // nie gesagt - zählt nicht
    form.costEur = 16.52 // unverändert

    expect(correctedFields(snap, form)).toEqual(['odometerKm'])
  })

  it('treats float noise as unchanged and a cleared value as corrected', () => {
    const form = emptyLogForm()
    form.kwhCharged = 41.3; form.socAfterChargePercent = 80
    const snap = spokenSnapshot(form, fields({ kwhCharged: 41.3, socAfter: 80 }))
    form.kwhCharged = 41.300000001
    form.socAfterChargePercent = null
    expect(correctedFields(snap, form)).toEqual(['socAfter'])
  })
})

describe('mergeUncertain', () => {
  it('a second recording clears doubts on what it said again and adds its own', () => {
    const f = { kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: 31270, costEur: null, pricePerKwh: null,
      loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
      spokenAddress: null, uncertain: ['tireType'] } as VoiceDraftFields
    expect(mergeUncertain(['odometerKm', 'costEur'], f)).toEqual(['costEur', 'tireType'])
  })
})

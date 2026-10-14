// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import { analytics } from '../../services/analytics'
import { useDrivingProfile } from '../useDrivingProfile'
import { NEEDS_STORAGE_KEY } from '../useModelRanking'

const stored = () => JSON.parse(localStorage.getItem(NEEDS_STORAGE_KEY) ?? '{}')

describe('useDrivingProfile', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(analytics.track).mockReset()
  })

  it('starts with every slider open: a start value is no answer', () => {
    const p = useDrivingProfile({}, () => 'VW ID.3')
    expect(p.openQuestions.value).toEqual(['annualKm', 'highwayShare', 'commute', 'commuteDays', 'longTrip'])
    expect(p.answers.homeCharging).toBe(true)
  })

  it('an answer closes its slider and goes back to the ranking, all five questions', () => {
    const p = useDrivingProfile({}, () => 'VW ID.3')
    p.answer('commute', 35)
    p.answer('annualKm', 20000)
    expect(p.openQuestions.value).toEqual(['highwayShare', 'commuteDays', 'longTrip'])
    expect(stored().dailyKm).toBe(35)
    expect(stored().annualKm).toBe(20000)
    expect(analytics.track).toHaveBeenCalledWith('personal_range_started', { model: 'VW ID.3' })
  })

  it('the wallbox start value never reaches the ranking, an active answer does', () => {
    const p = useDrivingProfile({}, () => 'VW ID.3')
    p.answer('longTrip', 800)
    expect(stored().homeCharging ?? null).toBeNull()
    p.answerHome(false)
    expect(p.answers.homeCharging).toBe(false)
    expect(stored().homeCharging).toBe(false)
  })

  it('the ranking needs check counts as answered, snapped onto the slider', () => {
    localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify({ dailyKm: 72, commuteDays: 3, longestTripKm: 610, homeCharging: false }))
    const p = useDrivingProfile({}, () => 'VW ID.3')
    expect(p.answers.commuteKmPerDay).toBe(70)
    expect(p.answers.longTripKm).toBe(600)
    expect(p.answers.homeCharging).toBe(false)
    expect(p.openQuestions.value).toEqual(['annualKm', 'highwayShare'])
  })

  it('a chip from the partner tile wins over the stored needs', () => {
    localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify({ dailyKm: 72 }))
    const p = useDrivingProfile({ pendeln: '40', urlaub: '1000' }, () => 'VW ID.3')
    expect(p.answers.commuteKmPerDay).toBe(40)
    expect(p.answers.longTripKm).toBe(1000)
  })

  it('a stored profile answers every slider; one from before the commute-days question leaves that one open', () => {
    const p = useDrivingProfile({}, () => 'VW ID.3')
    p.applyProfile({ commuteKmPerDay: 42, highwaySharePercent: 33, homeCharging: false, longTripKm: 2000, annualKm: 21_400, pricePerKwh: 0.31 })
    expect(p.answers).toMatchObject({ commuteKmPerDay: 40, highwayShare: 35, longTripKm: 1200, annualKm: 21_000, homeCharging: false, pricePerKwh: 0.31 })
    expect(p.openQuestions.value).toEqual(['commuteDays'])
    // a profile is no answer of the reader: nothing goes back to the ranking
    expect(localStorage.getItem(NEEDS_STORAGE_KEY)).toBeNull()
  })
})

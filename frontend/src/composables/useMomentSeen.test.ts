// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useMomentSeen, MOMENTS_SEEN_KEY } from './useMomentSeen'
import { DAY } from './storyMoments'

const NOW = new Date(2026, 9, 24, 12).getTime()

describe('useMomentSeen', () => {
  beforeEach(() => localStorage.clear())

  it('merkt den ersten Zeitpunkt, ein zweites Zeigen überschreibt ihn nicht', () => {
    const { seen, markShown } = useMomentSeen()
    markShown('just_charged:l1', NOW)
    markShown('just_charged:l1', NOW + 1000)
    expect(seen.value.shown['just_charged:l1']).toBe(NOW)
    expect(JSON.parse(localStorage.getItem(MOMENTS_SEEN_KEY)!).shown['just_charged:l1']).toBe(NOW)
  })

  it('Wegklicken gilt für die angegebene Dauer', () => {
    const { seen, dismiss } = useMomentSeen()
    dismiss('silence:l', NOW, 30 * DAY)
    expect(seen.value.dismissed['silence:l']).toBe(NOW + 30 * DAY)
  })

  it('räumt beim Laden Einträge älter als ein Jahr und abgelaufenes Wegklicken ab', () => {
    localStorage.setItem(MOMENTS_SEEN_KEY, JSON.stringify({
      shown: { old: NOW - 400 * DAY, fresh: NOW - DAY },
      dismissed: { gone: NOW - 1, active: NOW + DAY },
    }))
    const { seen } = useMomentSeen(NOW)
    expect(seen.value).toEqual({ shown: { fresh: NOW - DAY }, dismissed: { active: NOW + DAY } })
  })

  it('überlebt kaputtes JSON und einen gesperrten localStorage', () => {
    localStorage.setItem(MOMENTS_SEEN_KEY, '{kaputt')
    expect(useMomentSeen(NOW).seen.value).toEqual({ shown: {}, dismissed: {} })
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    expect(() => useMomentSeen(NOW).markShown('x', NOW)).not.toThrow()
    spy.mockRestore()
  })
})

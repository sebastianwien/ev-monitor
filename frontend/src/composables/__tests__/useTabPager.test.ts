import { describe, it, expect } from 'vitest'
import { pagerNavTarget } from '../useTabPager'
import { SWIPE_THRESHOLD_PX } from '../useSwipeBack'

describe('pagerNavTarget', () => {
  it('Wisch nach links auf Dashboard -> Logs', () => {
    expect(pagerNavTarget(-SWIPE_THRESHOLD_PX, false)).toBe('/logs')
    expect(pagerNavTarget(-200, false)).toBe('/logs')
  })

  it('Wisch nach rechts auf Logs -> Dashboard', () => {
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX, true)).toBe('/dashboard')
    expect(pagerNavTarget(200, true)).toBe('/dashboard')
  })

  it('kein Nachbar in Wischrichtung -> null', () => {
    // schon auf Logs, weiter nach links gibt es nichts
    expect(pagerNavTarget(-200, true)).toBeNull()
    // schon auf Dashboard, weiter nach rechts gibt es nichts
    expect(pagerNavTarget(200, false)).toBeNull()
  })

  it('unter der Schwelle -> null (kein versehentliches Umschalten)', () => {
    expect(pagerNavTarget(-(SWIPE_THRESHOLD_PX - 1), false)).toBeNull()
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX - 1, true)).toBeNull()
    expect(pagerNavTarget(0, false)).toBeNull()
  })
})

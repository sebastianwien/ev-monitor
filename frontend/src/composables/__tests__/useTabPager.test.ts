import { describe, it, expect } from 'vitest'
import { pagerNavTarget } from '../useTabPager'
import { SWIPE_THRESHOLD_PX } from '../useSwipeBack'

const CAR_TABS = ['/cars', '/charging-providers']
const CONTEXT_TABS = ['/dashboard', '/logs']

describe('pagerNavTarget', () => {
  it('Wisch nach links -> rechter Nachbar', () => {
    expect(pagerNavTarget(-SWIPE_THRESHOLD_PX, 0, CONTEXT_TABS)).toBe('/logs')
    expect(pagerNavTarget(-200, 0, CAR_TABS)).toBe('/charging-providers')
  })

  it('Wisch nach rechts -> linker Nachbar', () => {
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX, 1, CONTEXT_TABS)).toBe('/dashboard')
    expect(pagerNavTarget(200, 1, CAR_TABS)).toBe('/cars')
  })

  it('kein Nachbar in Wischrichtung -> null', () => {
    // schon auf dem rechten Tab, weiter nach links gibt es nichts
    expect(pagerNavTarget(-200, 1, CAR_TABS)).toBeNull()
    // schon auf dem linken Tab, weiter nach rechts gibt es nichts
    expect(pagerNavTarget(200, 0, CAR_TABS)).toBeNull()
  })

  it('unter der Schwelle -> null (kein versehentliches Umschalten)', () => {
    expect(pagerNavTarget(-(SWIPE_THRESHOLD_PX - 1), 0, CAR_TABS)).toBeNull()
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX - 1, 1, CAR_TABS)).toBeNull()
    expect(pagerNavTarget(0, 0, CAR_TABS)).toBeNull()
  })
})

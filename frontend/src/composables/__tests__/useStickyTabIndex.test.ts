import { describe, it, expect } from 'vitest'
import { nextTabIndex } from '../useStickyTabIndex'

const TABS = ['/dashboard', '/logs']

describe('nextTabIndex', () => {
  it('folgt Routen, die zum Layout gehoeren', () => {
    expect(nextTabIndex(0, '/logs', TABS)).toBe(1)
    expect(nextTabIndex(1, '/dashboard', TABS)).toBe(0)
  })

  it('haelt den Index, wenn die Route das Layout verlaesst', () => {
    // Beim Sprung zu den Ladekarten bleibt das Layout kurz stehen und wird ausgeblendet.
    // Wuerde der Index dabei auf 0 zurueckfallen, wischte der Pager das Dashboard herein
    // - gegen die Richtung des Uebergangs, den die Route gerade faehrt.
    expect(nextTabIndex(1, '/charging-providers', TABS)).toBe(1)
    expect(nextTabIndex(1, '/imports', TABS)).toBe(1)
    expect(nextTabIndex(0, '/cars', TABS)).toBe(0)
  })
})

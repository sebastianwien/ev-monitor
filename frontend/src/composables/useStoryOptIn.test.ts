// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('../services/analytics', () => ({ analytics: { track: vi.fn() } }))

import { useStoryOptIn, STORY_OPT_IN_KEY } from './useStoryOptIn'
import { analytics } from '../services/analytics'

describe('useStoryOptIn', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('ist standardmäßig aus', () => {
    expect(useStoryOptIn().enabled.value).toBe(false)
  })

  it('schreibt den Schalter und feuert ein Event', () => {
    const { enabled, set } = useStoryOptIn()
    set(true)
    expect(enabled.value).toBe(true)
    expect(localStorage.getItem(STORY_OPT_IN_KEY)).toBe('true')
    expect(analytics.track).toHaveBeenCalledWith('story_opt_in', { enabled: true })
  })

  it('überlebt einen kaputten localStorage', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    expect(useStoryOptIn().enabled.value).toBe(false)
    spy.mockRestore()
  })
})

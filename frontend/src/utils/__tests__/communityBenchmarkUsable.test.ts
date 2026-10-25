import { describe, it, expect } from 'vitest'
import { communityStatsUsable } from '../communityComparison'

describe('communityStatsUsable', () => {
  it('compares only from 5 drivers on (App-CI), not from 5 charges', () => {
    expect(communityStatsUsable({ logCount: 40, uniqueContributors: 1 })).toBe(false)
    expect(communityStatsUsable({ logCount: 40, uniqueContributors: 4 })).toBe(false)
    expect(communityStatsUsable({ logCount: 40, uniqueContributors: 5 })).toBe(true)
  })

  it('still needs 5 charges, a few drivers with one charge each are noise', () => {
    expect(communityStatsUsable({ logCount: 4, uniqueContributors: 5 })).toBe(false)
  })

  it('treats missing stats as unusable', () => {
    expect(communityStatsUsable(null)).toBe(false)
    expect(communityStatsUsable(undefined)).toBe(false)
  })
})

import { describe, it, expect } from 'vitest'
import { feedHints, MAX_FEED_HINTS, type FeedHintFlags } from '../feedHints'

const none: FeedHintFlags = { implausible: false, priceless: false, live: false, autosync: false, supporter: false, costTip: false }

describe('feedHints', () => {
  it('shows nothing without a reason', () => {
    expect(feedHints(none)).toEqual([])
  })

  it('puts the tasks before an offer and the tip last', () => {
    expect(feedHints({ ...none, costTip: true, supporter: true, priceless: true }))
      .toEqual(['priceless', 'supporter', 'cost_tip'])
  })

  it('keeps implausible before priceless, as both are tasks', () => {
    expect(feedHints({ ...none, priceless: true, implausible: true })).toEqual(['implausible', 'priceless'])
  })

  it('shows at most one offer, live before autosync before supporter', () => {
    expect(feedHints({ ...none, live: true, autosync: true, supporter: true })).toEqual(['live'])
    expect(feedHints({ ...none, autosync: true, supporter: true })).toEqual(['autosync'])
  })

  it(`caps the list at ${MAX_FEED_HINTS} rows, the tip goes first`, () => {
    const all = { implausible: true, priceless: true, live: false, autosync: true, supporter: false, costTip: true }
    expect(feedHints(all)).toEqual(['implausible', 'priceless', 'autosync'])
  })
})

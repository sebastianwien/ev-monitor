import { describe, it, expect } from 'vitest'
import { quotaNotice, quotaFromUsage, quotaFromLimit, type VoiceQuota } from '../voiceQuota'

const quota = (q: Partial<VoiceQuota>): VoiceQuota =>
  ({ plan: 'free', limit: 5, remaining: 5, exhausted: false, resetsOn: '2026-11-01', ...q })

describe('quotaNotice', () => {
  it('stays quiet while plenty is left', () => {
    expect(quotaNotice(quota({ remaining: 3 }))).toBeNull()
    expect(quotaNotice(quota({ plan: 'paid', limit: 30, remaining: 6 }))).toBeNull()
    expect(quotaNotice(null)).toBeNull()
  })

  it('free users see the last two with an upsell', () => {
    expect(quotaNotice(quota({ remaining: 2 }))).toEqual({ kind: 'low', remaining: 2, limit: 5, upsell: true })
  })

  it('paid users see the last five without an upsell', () => {
    expect(quotaNotice(quota({ plan: 'paid', limit: 30, remaining: 5 }))).toEqual({ kind: 'low', remaining: 5, limit: 30, upsell: false })
  })

  it('exhausted shows the reset date, upsell only for free', () => {
    expect(quotaNotice(quota({ remaining: 0, exhausted: true }))).toEqual({ kind: 'out', limit: 5, resetsOn: '2026-11-01', upsell: true })
    expect(quotaNotice(quota({ plan: 'paid', limit: 30, remaining: 0, exhausted: true })))
      .toEqual({ kind: 'out', limit: 30, resetsOn: '2026-11-01', upsell: false })
  })

  it('admins never see a counter, only the silent cap', () => {
    expect(quotaNotice(quota({ plan: 'admin', limit: null, remaining: null }))).toBeNull()
    expect(quotaNotice(quota({ plan: 'admin', limit: null, remaining: null, exhausted: true })))
      .toEqual({ kind: 'out', limit: null, resetsOn: '2026-11-01', upsell: false })
  })
})

describe('quota updates', () => {
  it('takes the stand after a recording from the draft usage', () => {
    expect(quotaFromUsage({ plan: 'free', limit: 5, remaining: 0, resetsOn: '2026-11-01' }))
      .toEqual(quota({ remaining: 0, exhausted: true }))
    expect(quotaFromUsage({ plan: 'admin', limit: null, remaining: null, resetsOn: '2026-11-01' }).exhausted).toBe(false)
  })

  it('a limit error means exhausted', () => {
    expect(quotaFromLimit({ kind: 'limit', plan: 'paid', limit: 30, resetsOn: '2026-11-01' }))
      .toEqual(quota({ plan: 'paid', limit: 30, remaining: 0, exhausted: true }))
  })
})

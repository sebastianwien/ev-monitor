import { describe, it, expect, vi } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))

import { mixPersonal, loadTickerRaw, tickerTarget, type RawTickerItem } from '../useTickerItems'

const stat = (key: string): RawTickerItem => ({ type: 'STAT', messageKey: key, params: {}, variant: 'energy' })
const mine = (key: string): RawTickerItem => ({ type: 'PERSONAL', messageKey: key, params: {}, variant: 'personal' })

describe('mixPersonal', () => {
  it('setzt persönliche Einträge an Position 2, 6 und 10', () => {
    const community = Array.from({ length: 10 }, (_, i) => stat(`c${i}`))
    const mixed = mixPersonal(community, [mine('p0'), mine('p1'), mine('p2')])

    expect(mixed.map(i => i.messageKey)).toEqual([
      'c0', 'p0', 'c1', 'c2', 'c3', 'p1', 'c4', 'c5', 'c6', 'p2', 'c7', 'c8', 'c9',
    ])
  })

  it('hängt persönliche Einträge an, wenn die Community-Liste kurz ist', () => {
    const mixed = mixPersonal([stat('c0')], [mine('p0'), mine('p1')])
    expect(mixed.map(i => i.messageKey)).toEqual(['c0', 'p0', 'p1'])
  })

  it('lässt die Community-Liste ohne persönliche Einträge unverändert', () => {
    const community = [stat('c0'), stat('c1')]
    expect(mixPersonal(community, [])).toEqual(community)
  })
})

describe('loadTickerRaw', () => {
  const today = (key: string): RawTickerItem => ({ type: 'STAT', messageKey: key, params: {}, variant: 'money' })
  const community = Array.from({ length: 6 }, (_, i) => stat(`c${i}`))

  function api(overrides: Record<string, () => Promise<{ data: RawTickerItem[] }>> = {}) {
    const defaults: Record<string, () => Promise<{ data: RawTickerItem[] }>> = {
      '/public/leaderboard/ticker': () => Promise.resolve({ data: community }),
      '/ticker/today': () => Promise.resolve({ data: [today('today_charge')] }),
      '/ticker/me': () => Promise.resolve({ data: [mine('my_month')] }),
    }
    return vi.fn((url: string) => ({ ...defaults, ...overrides })[url]())
  }

  it('ohne Nutzer-Einträge nur der öffentliche Endpoint (Landing Page)', async () => {
    const get = api()

    const raw = await loadTickerRaw(get, false)

    expect(get).toHaveBeenCalledTimes(1)
    expect(raw).toEqual(community)
  })

  it('lädt alle drei Endpoints und mischt Heute-Einträge und persönliche Einträge ein', async () => {
    const raw = await loadTickerRaw(api(), true)

    expect(raw.map(i => i.messageKey)).toEqual(['c0', 'my_month', 'c1', 'c2', 'today_charge', 'c3', 'c4', 'c5'])
  })

  it('blendet bei Fehler im persönlichen Teil nur diesen aus', async () => {
    const raw = await loadTickerRaw(api({ '/ticker/me': () => Promise.reject(new Error('500')) }), true)

    expect(raw.map(i => i.messageKey)).toEqual(['c0', 'c1', 'c2', 'today_charge', 'c3', 'c4', 'c5'])
  })

  it('zeigt die persönlichen Einträge, wenn der Community-Teil ausfällt', async () => {
    const raw = await loadTickerRaw(api({
      '/public/leaderboard/ticker': () => Promise.reject(new Error('500')),
      '/ticker/today': () => Promise.reject(new Error('500')),
    }), true)

    expect(raw.map(i => i.messageKey)).toEqual(['my_month'])
  })
})

describe('tickerTarget', () => {
  it('verlinkt nur Einträge mit echter Zielseite', () => {
    expect(tickerTarget({ type: 'LEADER', messageKey: 'leader', params: {}, variant: 'leader' })).toBe('/leaderboard')
    expect(tickerTarget(mine('my_rank'))).toBe('/leaderboard')
    expect(tickerTarget(mine('my_rank_leader'))).toBe('/leaderboard')
    expect(tickerTarget(mine('my_month'))).toBe('/dashboard')
    expect(tickerTarget(mine('my_consumption'))).toBe('/dashboard')
    expect(tickerTarget(stat('co2_saved'))).toBeUndefined()
    expect(tickerTarget(stat('today_charge'))).toBeUndefined()
    expect(tickerTarget({ type: 'NEWS', variant: 'news', text: 'x', url: 'https://x' })).toBeUndefined()
  })
})

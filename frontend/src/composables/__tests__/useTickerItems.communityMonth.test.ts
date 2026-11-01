import { describe, it, expect, vi } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))

import { loadCommunityMonthRaw, pickCommunitySummary, type RawTickerItem, type TickerItem } from '../useTickerItems'

const raw = (key: string): RawTickerItem => ({ type: 'STAT', messageKey: key, params: {}, variant: 'energy' })
const item = (key: string, type: TickerItem['type'] = 'STAT'): TickerItem => ({ type, key, text: key, variant: 'energy' })

describe('loadCommunityMonthRaw', () => {
  it('holt nur die Community-Liste des genannten Monats, ohne Heute- und persönliche Einträge', async () => {
    const get = vi.fn((url: string) => Promise.resolve({ data: url.includes('month=2026-10') ? [raw('stat_base')] : [] }))

    const items = await loadCommunityMonthRaw(get, '2026-10')

    expect(get).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledWith('/public/leaderboard/ticker?month=2026-10')
    expect(items.map(i => i.messageKey)).toEqual(['stat_base'])
  })

  it('liefert eine leere Liste, wenn der Abruf scheitert', async () => {
    const get = vi.fn(() => Promise.reject(new Error('offline')))
    expect(await loadCommunityMonthRaw(get, '2026-10')).toEqual([])
  })
})

describe('pickCommunitySummary', () => {
  it('ordnet nach Aussagekraft: Energie, Ersparnis, Vergleiche, Orte, und nimmt alle Kategorien', () => {
    const picked = pickCommunitySummary([item('wind'), item('top_provider'), item('home_quota'), item('households'), item('savings_vs_fuel'), item('co2_saved'), item('solar'), item('charge_time'), item('stat_base')])
    expect(picked.map(i => i.key)).toEqual(['stat_base', 'savings_vs_fuel', 'co2_saved', 'households', 'wind', 'solar', 'charge_time', 'home_quota', 'top_provider'])
  })

  it('lässt Führende und persönliche Einträge weg', () => {
    const picked = pickCommunitySummary([item('leader', 'LEADER'), item('my_month', 'PERSONAL'), item('stat_base')])
    expect(picked.map(i => i.key)).toEqual(['stat_base'])
  })
})

import { describe, it, expect } from 'vitest'
import { chargeGroupsOf, groupKeysOf } from '../chargeGroups'

const at = (d: number, h: number) => new Date(2026, 9, d, h).toISOString()
const log = (id: string, d: number, h: number, over: Record<string, unknown> = {}) => ({
  id, loggedAt: at(d, h), dataSource: 'USER_LOGGED', odometerKm: null as number | null, includeInStatistics: true, ...over,
})
const ids = (groups: { id: string }[][]) => groups.map(g => g.map(l => l.id))

describe('chargeGroupsOf (Ladegruppen wie in der Liste)', () => {
  it('go-e und API ohne km-Stand: je Tag eine Gruppe, erst ab zwei', () => {
    const g = chargeGroupsOf([
      log('a', 5, 9, { dataSource: 'WALLBOX_GOE' }), log('b', 5, 11, { dataSource: 'WALLBOX_GOE' }),
      log('c', 5, 13, { dataSource: 'API_UPLOAD' }), log('d', 6, 9, { dataSource: 'WALLBOX_GOE' }),
    ])
    expect(ids(g.byDay)).toEqual([['a', 'b', 'c']])
    expect(g.singles.map(l => l.id)).toEqual(['d'])
  })

  it('gleicher km-Stand hintereinander: eine Gruppe, neueste zuerst; ein anderer km-Stand dazwischen trennt', () => {
    const g = chargeGroupsOf([
      log('a', 5, 9, { odometerKm: 1000 }), log('b', 5, 11, { odometerKm: 1000 }),
      log('c', 6, 9, { odometerKm: 1100 }), log('d', 7, 9, { odometerKm: 1000 }),
    ])
    expect(ids(g.byOdometer)).toEqual([['b', 'a']])
    expect(g.singles.map(l => l.id)).toEqual(['d', 'c'])
  })

  it('go-e mit km-Stand zählt zum km-Stand, nicht zum Tag', () => {
    const g = chargeGroupsOf([
      log('a', 5, 9, { dataSource: 'WALLBOX_GOE', odometerKm: 1000 }), log('b', 5, 11, { dataSource: 'WALLBOX_GOE', odometerKm: 1000 }),
    ])
    expect(g.byDay).toEqual([])
    expect(ids(g.byOdometer)).toEqual([['b', 'a']])
  })

  it('implausibel und nicht in der Statistik: weder Gruppe noch einzeln', () => {
    const g = chargeGroupsOf([
      log('a', 5, 9, { odometerKm: 1000 }), log('b', 5, 11, { odometerKm: 1000, includeInStatistics: false, consumptionImplausible: true }),
    ])
    expect(g.byOdometer).toEqual([])
    expect(g.singles.map(l => l.id)).toEqual(['a'])
  })
})

describe('groupKeysOf', () => {
  it('je Ladung einer Gruppe dieselbe Kennung, einzelne Ladungen fehlen', () => {
    const keys = groupKeysOf([
      log('a', 5, 9, { odometerKm: 1000 }), log('b', 5, 11, { odometerKm: 1000 }), log('c', 6, 9, { odometerKm: 1100 }),
    ])
    expect(keys.get('a')).toBe(keys.get('b'))
    expect(keys.has('a')).toBe(true)
    expect(keys.has('c')).toBe(false)
  })
})

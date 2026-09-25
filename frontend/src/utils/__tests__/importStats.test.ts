import { describe, it, expect } from 'vitest'
import {
  sortGroups,
  dailySeries,
  formatAgo,
  formatRate,
  sourceLabel,
  HEALTH_LABEL,
  type ImportGroup,
} from '../importStats'

const group = (over: Partial<ImportGroup>): ImportGroup => ({
  provider: 'TESLA',
  channel: 'LIVE',
  health: 'OK',
  events: 1,
  errorRate: 0,
  sessionsImported: 0,
  sessionsSkipped: 0,
  sessionsFailed: 0,
  tripsImported: 0,
  tripsSkipped: 0,
  outcomes: {},
  lastEventAt: null,
  lastSuccessAt: null,
  topErrors: [],
  ...over,
})

describe('sortGroups', () => {
  it('puts disturbed sources first, then by volume', () => {
    const sorted = sortGroups([
      group({ provider: 'A', health: 'OK', events: 50 }),
      group({ provider: 'B', health: 'ERROR', events: 2 }),
      group({ provider: 'C', health: 'WARN', events: 10 }),
      group({ provider: 'D', health: 'OK', events: 90 }),
    ])
    expect(sorted.map((g) => g.provider)).toEqual(['B', 'C', 'D', 'A'])
  })
})

describe('dailySeries', () => {
  it('fills every day of the range and stacks outcomes', () => {
    const series = dailySeries(
      [
        { date: '2026-09-24', outcome: 'IMPORTED', count: 3 },
        { date: '2026-09-25', outcome: 'IMPORTED', count: 1 },
        { date: '2026-09-25', outcome: 'PARSE_ERROR', count: 2 },
      ],
      3,
      new Date(2026, 8, 25, 12),
    )
    expect(series.labels).toEqual(['23.09.', '24.09.', '25.09.'])
    expect(series.datasets.map((d) => d.outcome)).toEqual(['IMPORTED', 'PARSE_ERROR'])
    expect(series.datasets[0].data).toEqual([0, 3, 1])
    expect(series.datasets[1].data).toEqual([0, 0, 2])
  })
})

describe('formatAgo', () => {
  const now = Date.parse('2026-09-25T12:00:00Z')

  it('says "nie" without a timestamp', () => {
    expect(formatAgo(null, now)).toBe('nie')
  })

  it('formats hours ago in German', () => {
    expect(formatAgo('2026-09-25T09:00:00Z', now)).toBe('vor 3 Std.')
  })
})

describe('labels', () => {
  it('formats the rate as percent with German decimals', () => {
    expect(formatRate(0.125)).toBe('12,5 %')
    expect(formatRate(0)).toBe('0 %')
  })

  it('names provider and channel, unknown values fall through', () => {
    expect(sourceLabel('VW_GROUP', 'SYNC')).toBe('VW Group · Sync')
    expect(sourceLabel('NEW_ONE', 'UNKNOWN')).toBe('NEW_ONE · Unbekannt')
  })

  it('names every health state in words, not only by color', () => {
    expect(HEALTH_LABEL).toEqual({ OK: 'OK', WARN: 'Auffällig', ERROR: 'Gestört' })
  })
})

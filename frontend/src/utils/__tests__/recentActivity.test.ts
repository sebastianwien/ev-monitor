import { describe, expect, it } from 'vitest'
import {
  latestChargeEntry,
  latestTripEntry,
  normalizeCharge,
  relativeTimeParts,
} from '../recentActivity'

describe('latestChargeEntry', () => {
  it('returns null for non-array input', () => {
    expect(latestChargeEntry(null)).toBeNull()
    expect(latestChargeEntry(undefined)).toBeNull()
  })

  it('returns the first non-trip entry of a descending feed', () => {
    const feed = [
      { id: 't1', _isTrip: true },
      { id: 'c1', _isTrip: false },
      { id: 'c2', _isTrip: false },
    ]
    expect(latestChargeEntry(feed)?.id).toBe('c1')
  })

  it('treats a Ladegruppe as a charge', () => {
    const feed = [{ id: 'g1', _isTrip: false, _isLadegruppe: true }]
    expect(latestChargeEntry(feed)?.id).toBe('g1')
  })

  it('returns null when the feed has only trips', () => {
    expect(latestChargeEntry([{ id: 't1', _isTrip: true }])).toBeNull()
  })
})

describe('latestTripEntry', () => {
  it('returns the first trip entry of a descending feed', () => {
    const feed = [
      { id: 'c1', _isTrip: false },
      { id: 't1', _isTrip: true },
      { id: 't2', _isTrip: true },
    ]
    expect(latestTripEntry(feed)?.id).toBe('t1')
  })

  it('returns null when there are no trips', () => {
    expect(latestTripEntry([{ id: 'c1', _isTrip: false }])).toBeNull()
    expect(latestTripEntry(null)).toBeNull()
  })
})

describe('normalizeCharge', () => {
  it('returns null for null input', () => {
    expect(normalizeCharge(null)).toBeNull()
  })

  it('flattens a single log, preferring netto kWh and deriving €/kWh', () => {
    const n = normalizeCharge({
      id: 5,
      loggedAt: '2026-07-20T10:00:00Z',
      kwhCharged: 25,
      kwhAtVehicle: 23.4,
      costEur: 8.19,
      socBeforeChargePercent: 42,
      socAfterChargePercent: 80,
      maxChargingPowerKw: 210,
      chargingType: 'DC',
      dataSource: 'TESLA_LIVE',
      chargeDurationMinutes: 32,
    })!
    expect(n.isGroup).toBe(false)
    expect(n.kwh).toBe(23.4)
    expect(n.costEur).toBe(8.19)
    expect(n.costKwh).toBeCloseTo(8.19 / 23.4, 5)
    expect(n.socBefore).toBe(42)
    expect(n.socAfter).toBe(80)
    expect(n.maxPowerKw).toBe(210)
    expect(n.chargingType).toBe('DC')
    expect(n.dataSource).toBe('TESLA_LIVE')
    expect(n.durationMinutes).toBe(32)
  })

  it('falls back to brutto kWh when netto is missing', () => {
    const n = normalizeCharge({ id: 1, kwhCharged: 20, kwhAtVehicle: null, costEur: null })!
    expect(n.kwh).toBe(20)
    expect(n.costKwh).toBeNull()
  })

  it('uses aggregate fields for a Ladegruppe', () => {
    const n = normalizeCharge({
      id: 'g1',
      _isLadegruppe: true,
      _totalKwh: 40,
      _totalCostEur: 12,
      _costKwh: 0.3,
      _maxSoc: 90,
      _maxPower: 150,
      _commonDataSource: 'WALLBOX_GOE',
      socBeforeChargePercent: 20,
      chargingType: 'AC',
      dataSource: 'SHOULD_BE_IGNORED_FOR_GROUP',
    })!
    expect(n.isGroup).toBe(true)
    expect(n.kwh).toBe(40)
    expect(n.costEur).toBe(12)
    expect(n.costKwh).toBe(0.3)
    expect(n.socBefore).toBe(20)
    expect(n.socAfter).toBe(90)
    expect(n.maxPowerKw).toBe(150)
    expect(n.dataSource).toBe('WALLBOX_GOE')
    expect(n.durationMinutes).toBeNull()
  })

  it('parses numeric strings (API decimals arrive as strings)', () => {
    const n = normalizeCharge({ id: 1, kwhAtVehicle: '23.40', costEur: '8.19' })!
    expect(n.kwh).toBe(23.4)
    expect(n.costEur).toBe(8.19)
  })
})

describe('relativeTimeParts', () => {
  const now = 1_700_000_000_000

  it('returns null for non-finite input', () => {
    expect(relativeTimeParts(NaN, now)).toBeNull()
  })

  it('reports seconds for a very recent past', () => {
    expect(relativeTimeParts(now - 30_000, now)).toEqual({ value: -30, unit: 'second' })
  })

  it('reports minutes', () => {
    expect(relativeTimeParts(now - 5 * 60_000, now)).toEqual({ value: -5, unit: 'minute' })
  })

  it('reports hours', () => {
    expect(relativeTimeParts(now - 3 * 3_600_000, now)).toEqual({ value: -3, unit: 'hour' })
  })

  it('reports days', () => {
    expect(relativeTimeParts(now - 2 * 86_400_000, now)).toEqual({ value: -2, unit: 'day' })
  })

  it('reports weeks', () => {
    expect(relativeTimeParts(now - 2 * 7 * 86_400_000, now)).toEqual({ value: -2, unit: 'week' })
  })

  it('reports months', () => {
    expect(relativeTimeParts(now - 2 * 30 * 86_400_000, now)).toEqual({ value: -2, unit: 'month' })
  })
})

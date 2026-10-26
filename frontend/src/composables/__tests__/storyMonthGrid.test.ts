import { describe, it, expect } from 'vitest'
import { chargeMonths, earliestOffset, monthGrid, monthOffset } from '../storyMonthGrid'

const now = new Date(2026, 9, 26) // 26.10.2026
const day = (y: number, m: number, d = 3) => ({ timestamp: new Date(y, m - 1, d, 12).toISOString(), charges: 1, distanceKm: null, consumptionKwhPer100km: null })

describe('storyMonthGrid', () => {
  it('rechnet Monate als Abstand zum laufenden', () => {
    expect(monthOffset(2026, 10, now)).toBe(0)
    expect(monthOffset(2026, 8, now)).toBe(-2)
    expect(monthOffset(2025, 3, now)).toBe(-19)
  })

  it('kennt die Monate mit Ladungen und den frühesten, mindestens die drei des Schalters', () => {
    const months = chargeMonths([day(2025, 3), day(2025, 3, 20), day(2026, 9)])
    expect([...months].sort()).toEqual(['2025-3', '2026-9'])
    expect(earliestOffset(months, now)).toBe(-19)
    expect(earliestOffset(new Set(), now)).toBe(-2)
  })

  it('Raster: Jahre absteigend, je zwölf Monate; wählbar nur Monate mit Ladungen bis heute und die drei des Schalters', () => {
    const months = chargeMonths([day(2025, 3), day(2026, 5)])
    const grid = monthGrid(now, months, earliestOffset(months, now))
    expect(grid.map(y => y.year)).toEqual([2026, 2025])
    expect(grid[0].months).toHaveLength(12)
    const at = (y: number, m: number) => grid.find(g => g.year === y)!.months[m - 1]
    expect(at(2026, 5).enabled).toBe(true)
    expect(at(2026, 6).enabled).toBe(false) // keine Ladung
    expect(at(2026, 8).enabled).toBe(true) // Teil des Schalters
    expect(at(2026, 11).enabled).toBe(false) // Zukunft
    expect(at(2025, 2).enabled).toBe(false) // vor der ersten Ladung
    expect(at(2025, 3)).toMatchObject({ enabled: true, offset: -19 })
  })
})

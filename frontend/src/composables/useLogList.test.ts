import { describe, it, expect } from 'vitest'
import { MERGE_WINDOW_MS, isWithinMergeWindow } from './useLogList'

// ── Merge-Fenster ─────────────────────────────────────────────────────────────
// Das Fenster begrenzt, welche Logs im Zusammenführen-Modal als Kandidaten
// erscheinen. Es muss lange AC-Ladevorgänge abdecken (z. B. 14h an 4 kW).

describe('merge window', () => {
  const base = '2026-07-10T08:00:00Z'
  const plusHours = (h: number) => new Date(Date.parse(base) + h * 60 * 60 * 1000).toISOString()

  it('spans 24 hours', () => {
    expect(MERGE_WINDOW_MS).toBe(24 * 60 * 60 * 1000)
  })

  it('accepts a 14h AC charge - the case the 12h window rejected', () => {
    expect(isWithinMergeWindow(base, plusHours(14))).toBe(true)
  })

  it('accepts logs in both directions', () => {
    expect(isWithinMergeWindow(base, plusHours(-18))).toBe(true)
    expect(isWithinMergeWindow(base, plusHours(18))).toBe(true)
  })

  it('accepts a log exactly on the boundary', () => {
    expect(isWithinMergeWindow(base, plusHours(24))).toBe(true)
  })

  it('rejects a log beyond the window', () => {
    expect(isWithinMergeWindow(base, plusHours(25))).toBe(false)
    expect(isWithinMergeWindow(base, plusHours(-25))).toBe(false)
  })
})

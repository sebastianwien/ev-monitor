import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { useFeedWindow, FEED_RANGE_DEFAULT, FEED_RESOLUTION_KEY, FEED_TIME_RANGE_KEY } from '../useFeedWindow'

// Der Log-Feed laedt ein Zeitfenster statt einer Seitengroesse. Ein Zeitraum gilt fuer alle
// Darstellungen (Tag/Woche/Monat/Einzeln) - der Wechsel aendert nur die Gruppierung.

const now = () => new Date('2026-09-10T08:00:00Z')

describe('useFeedWindow', () => {
  beforeEach(() => localStorage.clear())

  it('defaults to the last three months in every resolution', () => {
    expect(FEED_RANGE_DEFAULT).toBe('LAST_3_MONTHS')
    const feed = useFeedWindow(now)
    for (const r of ['day', 'week', 'month', 'cycle'] as const) {
      feed.resolution.value = r
      expect(feed.timeRange.value).toBe('LAST_3_MONTHS')
    }
  })

  it('keeps the chosen range when the resolution changes', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'day'
    feed.timeRange.value = 'LAST_6_MONTHS'
    feed.resolution.value = 'month'
    expect(feed.timeRange.value).toBe('LAST_6_MONTHS')
    feed.resolution.value = 'week'
    expect(feed.timeRange.value).toBe('LAST_6_MONTHS')
  })

  it('persists the range across sessions', async () => {
    const feed = useFeedWindow(now)
    feed.timeRange.value = 'LAST_MONTH'
    await nextTick()
    expect(localStorage.getItem(FEED_TIME_RANGE_KEY)).toBe('LAST_MONTH')
    expect(useFeedWindow(now).timeRange.value).toBe('LAST_MONTH')
  })

  it('migrates the old per-resolution map: the last used resolution wins', async () => {
    localStorage.setItem(FEED_RESOLUTION_KEY, 'week')
    localStorage.setItem(FEED_TIME_RANGE_KEY, JSON.stringify({ day: 'LAST_MONTH', week: 'LAST_12_MONTHS' }))
    const feed = useFeedWindow(now)
    expect(feed.timeRange.value).toBe('LAST_12_MONTHS')
    feed.resolution.value = 'day'
    expect(feed.timeRange.value).toBe('LAST_12_MONTHS')
    await nextTick()
    expect(localStorage.getItem(FEED_TIME_RANGE_KEY)).toBe('LAST_12_MONTHS')
  })

  it('ignores stored ranges the feed does not offer (ALL_TIME)', () => {
    localStorage.setItem(FEED_TIME_RANGE_KEY, 'ALL_TIME')
    expect(useFeedWindow(now).timeRange.value).toBe('LAST_3_MONTHS')
    localStorage.setItem(FEED_RESOLUTION_KEY, 'cycle')
    localStorage.setItem(FEED_TIME_RANGE_KEY, JSON.stringify({ cycle: 'ALL_TIME' }))
    expect(useFeedWindow(now).timeRange.value).toBe('LAST_3_MONTHS')
  })

  it('open-ended ranges send only `from` - logs carry naive local time, a UTC "now" would cut off today', () => {
    const feed = useFeedWindow(now)
    feed.timeRange.value = 'THIS_MONTH'
    expect(feed.queryParams.value).toBe('&from=2026-09-01T00:00:00.000Z')
  })

  it('closed ranges send both bounds', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
    feed.timeRange.value = 'LAST_MONTH'
    expect(feed.queryParams.value).toBe('&from=2026-08-01T00:00:00.000Z&to=2026-08-31T23:59:59.999Z')
  })

  it('loadOlder extends the window one month further back, per call', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
    feed.timeRange.value = 'THIS_MONTH'
    feed.loadOlder()
    expect(feed.queryParams.value.startsWith('&from=2026-08-01T00:00:00.000Z')).toBe(true)
    feed.loadOlder()
    expect(feed.queryParams.value.startsWith('&from=2026-07-01T00:00:00.000Z')).toBe(true)
    expect(feed.nextOlderMonth.value.toISOString().slice(0, 7)).toBe('2026-06')
  })

  it('resets the extension when the range changes, keeps it across resolutions', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
    feed.timeRange.value = 'THIS_MONTH'
    feed.loadOlder()
    feed.timeRange.value = 'LAST_MONTH'
    expect(feed.queryParams.value.startsWith('&from=2026-08-01T00:00:00.000Z')).toBe(true)
    feed.loadOlder()
    feed.resolution.value = 'day'
    expect(feed.queryParams.value.startsWith('&from=2026-07-01T00:00:00.000Z')).toBe(true)
  })

  it('CUSTOM without both dates falls back to the default range', () => {
    const feed = useFeedWindow(now)
    feed.timeRange.value = 'CUSTOM'
    expect(feed.queryParams.value.startsWith('&from=2026-07-01T00:00:00.000Z')).toBe(true)
    feed.customStartDate.value = '2026-05-01'
    feed.customEndDate.value = '2026-05-31'
    expect(feed.queryParams.value).toBe('&from=2026-05-01T00:00:00.000Z&to=2026-05-31T23:59:59.999Z')
  })
})

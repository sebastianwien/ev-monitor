import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { useFeedWindow, FEED_RANGE_DEFAULTS, FEED_TIME_RANGE_KEY } from '../useFeedWindow'

// Der Log-Feed laedt ein Zeitfenster statt einer Seitengroesse. Das Fenster haengt an der
// Darstellung (Tag/Woche/Monat/Einzeln), damit Nutzer mit vielen Fahrten nicht bei jedem
// Besuch hunderte Zeilen rendern.

const now = () => new Date('2026-09-10T08:00:00Z')

describe('useFeedWindow', () => {
  beforeEach(() => localStorage.clear())

  it('defaults: day/week/cycle show the current month, month shows three months', () => {
    expect(FEED_RANGE_DEFAULTS).toEqual({
      day: 'THIS_MONTH', week: 'THIS_MONTH', month: 'LAST_3_MONTHS', cycle: 'THIS_MONTH',
    })
    const feed = useFeedWindow(now)
    feed.resolution.value = 'month'
    expect(feed.timeRange.value).toBe('LAST_3_MONTHS')
    feed.resolution.value = 'cycle'
    expect(feed.timeRange.value).toBe('THIS_MONTH')
  })

  it('remembers the range per resolution', async () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'day'
    feed.timeRange.value = 'LAST_MONTH'
    feed.resolution.value = 'month'
    expect(feed.timeRange.value).toBe('LAST_3_MONTHS')
    feed.resolution.value = 'day'
    expect(feed.timeRange.value).toBe('LAST_MONTH')
    await nextTick()
    expect(JSON.parse(localStorage.getItem(FEED_TIME_RANGE_KEY)!)).toEqual({ day: 'LAST_MONTH' })
  })

  it('ignores stored ranges the feed does not offer (ALL_TIME)', () => {
    localStorage.setItem(FEED_TIME_RANGE_KEY, JSON.stringify({ cycle: 'ALL_TIME' }))
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
    expect(feed.timeRange.value).toBe('THIS_MONTH')
  })

  it('open-ended ranges send only `from` - logs carry naive local time, a UTC "now" would cut off today', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
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
    feed.loadOlder()
    expect(feed.queryParams.value.startsWith('&from=2026-08-01T00:00:00.000Z')).toBe(true)
    feed.loadOlder()
    expect(feed.queryParams.value.startsWith('&from=2026-07-01T00:00:00.000Z')).toBe(true)
    expect(feed.nextOlderMonth.value.toISOString().slice(0, 7)).toBe('2026-06')
  })

  it('resets the extension when range or resolution changes', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'cycle'
    feed.loadOlder()
    feed.timeRange.value = 'LAST_MONTH'
    expect(feed.queryParams.value.startsWith('&from=2026-08-01T00:00:00.000Z')).toBe(true)
    feed.loadOlder()
    feed.resolution.value = 'day'
    expect(feed.queryParams.value.startsWith('&from=2026-09-01T00:00:00.000Z')).toBe(true)
  })

  it('CUSTOM without both dates falls back to the resolution default', () => {
    const feed = useFeedWindow(now)
    feed.resolution.value = 'week'
    feed.timeRange.value = 'CUSTOM'
    expect(feed.queryParams.value.startsWith('&from=2026-09-01T00:00:00.000Z')).toBe(true)
    feed.customStartDate.value = '2026-05-01'
    feed.customEndDate.value = '2026-05-31'
    expect(feed.queryParams.value).toBe('&from=2026-05-01T00:00:00.000Z&to=2026-05-31T23:59:59.999Z')
  })
})

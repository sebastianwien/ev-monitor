import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { useFeedWindow, FEED_RANGE_DEFAULT, FEED_RESOLUTION_KEY, FEED_TIME_RANGE_KEY } from '../useFeedWindow'

// Der Log-Feed laedt ein Zeitfenster statt einer Seitengroesse. Ein Zeitraum gilt fuer alle
// Darstellungen (Tag/Woche/Monat/Einzeln) - der Wechsel aendert nur die Gruppierung.

const now = () => new Date('2026-09-10T08:00:00Z')

describe('useFeedWindow', () => {
  beforeEach(() => localStorage.clear())

  it('defaults to the current month in every resolution', () => {
    expect(FEED_RANGE_DEFAULT).toBe('THIS_MONTH')
    const feed = useFeedWindow(now)
    for (const r of ['day', 'week', 'month', 'cycle'] as const) {
      feed.resolution.value = r
      expect(feed.timeRange.value).toBe('THIS_MONTH')
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
    expect(useFeedWindow(now).timeRange.value).toBe('THIS_MONTH')
    localStorage.setItem(FEED_RESOLUTION_KEY, 'cycle')
    localStorage.setItem(FEED_TIME_RANGE_KEY, JSON.stringify({ cycle: 'ALL_TIME' }))
    expect(useFeedWindow(now).timeRange.value).toBe('THIS_MONTH')
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

  // Gruppen am Fensterrand sind immer vollstaendig: eine Woche, die am Monatsersten beginnt,
  // zeigte sonst nur ihre Tage im Fenster - mit falschen Summen unter einem ganzen Wochenkopf.
  describe('whole periods at the window edges', () => {
    it('week view starts on the Monday of the first week (2026-09-01 is a Tuesday)', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'week'
      feed.timeRange.value = 'THIS_MONTH'
      expect(feed.queryParams.value).toBe('&from=2026-08-31T00:00:00.000Z')
    })

    it('week view of a closed range also ends on the Sunday of the last week', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'week'
      feed.timeRange.value = 'LAST_MONTH'
      expect(feed.queryParams.value).toBe('&from=2026-07-27T00:00:00.000Z&to=2026-09-06T23:59:59.999Z')
    })

    it('month view widens a custom range to whole months', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'month'
      feed.timeRange.value = 'CUSTOM'
      feed.customStartDate.value = '2026-05-10'
      feed.customEndDate.value = '2026-06-20'
      expect(feed.queryParams.value).toBe('&from=2026-05-01T00:00:00.000Z&to=2026-06-30T23:59:59.999Z')
    })

    it('day and single view keep the window as chosen', () => {
      const feed = useFeedWindow(now)
      feed.timeRange.value = 'THIS_MONTH'
      for (const r of ['day', 'cycle'] as const) {
        feed.resolution.value = r
        expect(feed.queryParams.value).toBe('&from=2026-09-01T00:00:00.000Z')
      }
    })

    it('loading older in week view still steps whole months', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'week'
      feed.timeRange.value = 'THIS_MONTH'
      expect(feed.nextOlderMonth.value.toISOString().slice(0, 7)).toBe('2026-08')
      feed.loadOlder()
      // 2026-08-01 ist ein Samstag -> Montag davor
      expect(feed.queryParams.value).toBe('&from=2026-07-27T00:00:00.000Z')
      expect(feed.nextOlderMonth.value.toISOString().slice(0, 7)).toBe('2026-07')
    })
  })

  // Sieht der Feed leer aus, weitet LogsView den Zeitraum stufenweise: 1 -> 3 -> 6 -> 12 Monate.
  // Gilt nur fuer die Sitzung; eine eigene Wahl bleibt genau so, wie sie ist.
  describe('widenAutomatically', () => {
    it('steps through the wider presets and stops after twelve months', () => {
      const feed = useFeedWindow(now)
      const steps: string[] = []
      while (feed.widenAutomatically()) steps.push(feed.timeRange.value)
      expect(steps).toEqual(['LAST_3_MONTHS', 'LAST_6_MONTHS', 'LAST_12_MONTHS'])
      expect(feed.queryParams.value).toBe('&from=2025-10-01T00:00:00.000Z')
    })

    it('does not persist the automatic range', async () => {
      localStorage.setItem(FEED_TIME_RANGE_KEY, 'THIS_MONTH')
      const feed = useFeedWindow(now)
      feed.widenAutomatically()
      await nextTick()
      expect(feed.timeRange.value).toBe('LAST_3_MONTHS')
      expect(localStorage.getItem(FEED_TIME_RANGE_KEY)).toBe('THIS_MONTH')
      expect(useFeedWindow(now).timeRange.value).toBe('THIS_MONTH')
    })

    it('an explicit choice wins and switches the automatic off for the session', async () => {
      const feed = useFeedWindow(now)
      feed.widenAutomatically()
      feed.timeRange.value = 'THIS_MONTH'
      expect(feed.timeRange.value).toBe('THIS_MONTH')
      expect(feed.widenAutomatically()).toBe(false)
      expect(feed.timeRange.value).toBe('THIS_MONTH')
      await nextTick()
      expect(localStorage.getItem(FEED_TIME_RANGE_KEY)).toBe('THIS_MONTH')
    })

    it('never widens closed ranges', () => {
      for (const range of ['LAST_MONTH', 'CUSTOM']) {
        localStorage.setItem(FEED_TIME_RANGE_KEY, range)
        const feed = useFeedWindow(now)
        expect(feed.widenAutomatically()).toBe(false)
        expect(feed.timeRange.value).toBe(range)
      }
    })

    it('this year widens straight to twelve months', () => {
      localStorage.setItem(FEED_TIME_RANGE_KEY, 'THIS_YEAR')
      const feed = useFeedWindow(now)
      expect(feed.widenAutomatically()).toBe(true)
      expect(feed.timeRange.value).toBe('LAST_12_MONTHS')
    })

    it('stays out of the way once older months were loaded by hand', () => {
      const feed = useFeedWindow(now)
      feed.loadOlder()
      expect(feed.widenAutomatically()).toBe(false)
      expect(feed.queryParams.value).toBe('&from=2026-08-01T00:00:00.000Z')
    })
  })

  it('CUSTOM without both dates falls back to the default range', () => {
    const feed = useFeedWindow(now)
    feed.timeRange.value = 'CUSTOM'
    expect(feed.queryParams.value.startsWith('&from=2026-09-01T00:00:00.000Z')).toBe(true)
    feed.customStartDate.value = '2026-05-01'
    feed.customEndDate.value = '2026-05-31'
    expect(feed.queryParams.value).toBe('&from=2026-05-01T00:00:00.000Z&to=2026-05-31T23:59:59.999Z')
  })
})

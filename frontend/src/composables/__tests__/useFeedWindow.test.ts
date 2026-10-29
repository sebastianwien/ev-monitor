import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'
import { useFeedWindow, formatOlderWindow, FEED_RESOLUTION_KEY, type FeedResolution } from '../useFeedWindow'

// Der Log-Feed laedt ein festes Zeitfenster je Darstellung: Einzeln den laufenden Monat (wie die
// Uebersicht), Monat drei Monate, Woche acht Wochen, Tag zwei Wochen. "Aeltere laden" holt je ein
// weiteres Stueck derselben Groesse.

const now = () => new Date('2026-09-10T08:00:00Z') // Donnerstag
const from = (feed: ReturnType<typeof useFeedWindow>) => feed.queryParams.value

describe('useFeedWindow', () => {
  beforeEach(() => localStorage.clear())

  it('defaults to the month view and keeps the chosen view across sessions', async () => {
    const feed = useFeedWindow(now)
    expect(feed.resolution.value).toBe('month')
    feed.resolution.value = 'week'
    await nextTick()
    expect(localStorage.getItem(FEED_RESOLUTION_KEY)).toBe('week')
    expect(useFeedWindow(now).resolution.value).toBe('week')
  })

  describe('fixed window per view, open-ended (logs carry naive local time, a UTC "now" would cut off today)', () => {
    it('single: the running month, like the story', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'cycle'
      expect(from(feed)).toBe('&from=2026-09-01T00:00:00.000Z')
    })

    it('month: three whole months', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'month'
      expect(from(feed)).toBe('&from=2026-07-01T00:00:00.000Z')
    })

    it('week: eight whole weeks from Monday', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'week'
      // Montag dieser Woche 2026-09-07, sieben Wochen davor
      expect(from(feed)).toBe('&from=2026-07-20T00:00:00.000Z')
    })

    it('day: two weeks up to today', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'day'
      expect(from(feed)).toBe('&from=2026-08-28T00:00:00.000Z')
    })
  })

  describe('loadOlder', () => {
    it('steps one piece of the view per call', () => {
      const expected = {
        cycle: ['2026-08-01', '2026-07-01'],
        month: ['2026-04-01', '2026-01-01'],
        week: ['2026-05-25', '2026-03-30'],
        day: ['2026-08-14', '2026-07-31'],
      } as const
      for (const [view, steps] of Object.entries(expected)) {
        const feed = useFeedWindow(now)
        feed.resolution.value = view as keyof typeof expected
        for (const day of steps) {
          feed.loadOlder()
          expect(from(feed), view).toBe(`&from=${day}T00:00:00.000Z`)
        }
      }
    })

    it('a view change starts at that view\'s own window again', async () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'cycle'
      feed.loadOlder()
      feed.resolution.value = 'day'
      await nextTick()
      expect(from(feed)).toBe('&from=2026-08-28T00:00:00.000Z')
    })

    it('nextOlder is exactly the piece the next click adds', () => {
      const feed = useFeedWindow(now)
      feed.resolution.value = 'month'
      expect(new Date(feed.nextOlder.value.startMs).toISOString()).toBe('2026-04-01T00:00:00.000Z')
      expect(new Date(feed.nextOlder.value.endMs).toISOString()).toBe('2026-06-30T23:59:59.999Z')
      feed.resolution.value = 'day'
      expect(new Date(feed.nextOlder.value.startMs).toISOString()).toBe('2026-08-14T00:00:00.000Z')
      expect(new Date(feed.nextOlder.value.endMs).toISOString()).toBe('2026-08-27T23:59:59.999Z')
    })
  })

  describe('formatOlderWindow', () => {
    const win = (a: string, b: string) => ({ startMs: Date.parse(a), endMs: Date.parse(b) })

    it('single view names the month', () => {
      expect(formatOlderWindow(win('2026-08-01T00:00:00Z', '2026-08-31T23:59:59.999Z'), 'cycle', 'de')).toBe('Aug. 2026')
    })

    it('month view names the months, the year once if it is the same', () => {
      expect(formatOlderWindow(win('2026-04-01T00:00:00Z', '2026-06-30T23:59:59.999Z'), 'month', 'de')).toBe('Apr.-Juni 2026')
      expect(formatOlderWindow(win('2025-11-01T00:00:00Z', '2026-01-31T23:59:59.999Z'), 'month', 'de')).toBe('Nov. 2025-Jan. 2026')
    })

    it('week and day view name the days', () => {
      expect(formatOlderWindow(win('2026-08-14T00:00:00Z', '2026-08-27T23:59:59.999Z'), 'day', 'de')).toBe('14.-27. Aug.')
      expect(formatOlderWindow(win('2026-05-25T00:00:00Z', '2026-07-19T23:59:59.999Z'), 'week', 'de')).toBe('25. Mai-19. Juli')
    })
  })

  describe('feste Darstellung (Logbuch)', () => {
    it('lädt monatsweise, ohne die gespeicherte Wahl zu überschreiben', async () => {
      localStorage.setItem(FEED_RESOLUTION_KEY, 'week')
      const fixed = ref<FeedResolution | null>('cycle')
      const feed = useFeedWindow(now, fixed)
      expect(from(feed)).toBe('&from=2026-09-01T00:00:00.000Z')
      feed.loadOlder()
      expect(from(feed)).toBe('&from=2026-08-01T00:00:00.000Z')
      expect(feed.nextOlder.value.startMs).toBe(Date.parse('2026-07-01T00:00:00Z'))
      await nextTick()
      expect(localStorage.getItem(FEED_RESOLUTION_KEY)).toBe('week')
      expect(feed.resolution.value).toBe('week')
    })

    it('ohne feste Darstellung gilt wieder die Wahl, ab ihrem Standardfenster', () => {
      const fixed = ref<FeedResolution | null>('cycle')
      const feed = useFeedWindow(now, fixed)
      feed.loadOlder()
      fixed.value = null
      expect(from(feed)).toBe('&from=2026-07-01T00:00:00.000Z')
    })
  })
})


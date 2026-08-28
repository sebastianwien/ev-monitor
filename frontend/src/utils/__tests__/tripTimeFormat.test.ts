import { describe, it, expect } from 'vitest'
import { formatTripDateTimeRange, tripDateTimeParts, formatPauseDuration, tripDayLabel } from '../tripTimeFormat'

/**
 * Die Fahrtzeit ist die Ueberschrift einer Fahrt im Log-Feed: zuerst der Tag, dann die
 * Spanne. Die Faelle hier halten fest, wann welcher Teil erscheint.
 */
describe('formatTripDateTimeRange', () => {
  const start = '2026-08-20T13:31:00+02:00'
  const end = '2026-08-20T13:45:00+02:00'

  it('puts the date first, then the span - the day is what you scan for', () => {
    expect(formatTripDateTimeRange(start, end, 'de')).toBe('20.8., 13:31 - 13:45')
  })

  it('names both days when a drive crosses midnight', () => {
    const result = formatTripDateTimeRange(
      '2026-08-20T23:40:00+02:00', '2026-08-21T00:15:00+02:00', 'de')

    expect(result).toBe('20.8., 23:40 - 21.8., 00:15')
  })

  it('falls back to the arrival alone when no departure was recorded', () => {
    expect(formatTripDateTimeRange(null, end, 'de')).toBe('20.8., 13:45')
    expect(formatTripDateTimeRange(undefined, end, 'de')).toBe('20.8., 13:45')
  })

  it('follows the locale', () => {
    // Trennzeichen und Nullen im Datum bestimmt Intl je Sprache - festgehalten wird hier
    // nur, was uns gehoert: der Tag zuerst, danach die Spanne.
    for (const locale of ['en', 'sv', 'nb']) {
      const result = formatTripDateTimeRange(start, end, locale)
      expect(result).toMatch(/^20/)
      expect(result).toContain('13:31 - 13:45')
    }
  })

  it('returns an empty string without an arrival - there is nothing to show', () => {
    expect(formatTripDateTimeRange(start, null, 'de')).toBe('')
    expect(formatTripDateTimeRange(start, '', 'de')).toBe('')
  })

  describe('tripDateTimeParts', () => {
    it('separates the day from the span so each can be styled on its own', () => {
      expect(tripDateTimeParts(start, end, 'de')).toEqual({
        date: '20.8.',
        time: '13:31 - 13:45',
      })
    })

    it('keeps the second day inside the span when a drive crosses midnight', () => {
      expect(tripDateTimeParts('2026-08-20T23:40:00+02:00', '2026-08-21T00:15:00+02:00', 'de'))
        .toEqual({ date: '20.8.', time: '23:40 - 21.8., 00:15' })
    })

    it('shows the arrival alone when no departure was recorded', () => {
      expect(tripDateTimeParts(null, end, 'de')).toEqual({ date: '20.8.', time: '13:45' })
    })

    it('is empty without an arrival', () => {
      expect(tripDateTimeParts(start, null, 'de')).toEqual({ date: '', time: '' })
    })

    it('is what the joined string is built from', () => {
      const parts = tripDateTimeParts(start, end, 'de')
      expect(formatTripDateTimeRange(start, end, 'de')).toBe(`${parts.date}, ${parts.time}`)
    })
  })

  describe('formatPauseDuration', () => {
    it('bleibt unter einer Stunde bei Minuten', () => {
      expect(formatPauseDuration(7)).toBe('7 min')
      expect(formatPauseDuration(59)).toBe('59 min')
    })

    it('nennt Stunden und Minuten, sobald es laenger dauert', () => {
      expect(formatPauseDuration(163)).toBe('2 h 43 min')
      expect(formatPauseDuration(60)).toBe('1 h')
      expect(formatPauseDuration(180)).toBe('3 h')
    })

    it('rundet lange Ruhezeiten auf Stunden - die Minute interessiert dort niemanden', () => {
      expect(formatPauseDuration(14 * 60 + 20)).toBe('14 h')
    })

    it('ist leer, wo es nichts zu zeigen gibt', () => {
      expect(formatPauseDuration(null)).toBe('')
      expect(formatPauseDuration(0)).toBe('')
      expect(formatPauseDuration(-5)).toBe('')
    })
  })

  describe('tripDayLabel', () => {
    const heute = new Date('2026-08-26T10:00:00')

    it('nennt Wochentag und Datum - den Alltag erinnert man ueber den Wochentag', () => {
      expect(tripDayLabel('2026-08-20', 'de', heute)).toBe('Do 20.8.')
    })

    it('sagt Heute und Gestern, statt rechnen zu lassen', () => {
      expect(tripDayLabel('2026-08-26', 'de', heute)).toBe('Heute')
      expect(tripDayLabel('2026-08-25', 'de', heute)).toBe('Gestern')
    })

    it('folgt der Sprache', () => {
      expect(tripDayLabel('2026-08-20', 'en', heute)).toMatch(/^Thu /)
      expect(tripDayLabel('2026-08-26', 'en', heute)).toBe('Today')
      expect(tripDayLabel('2026-08-25', 'sv', heute)).toBe('Igår')
      expect(tripDayLabel('2026-08-26', 'nb', heute)).toBe('I dag')
    })

    it('bleibt stumm, wo kein Datum ist', () => {
      expect(tripDayLabel('unknown', 'de', heute)).toBe('')
      expect(tripDayLabel('', 'de', heute)).toBe('')
    })
  })
})

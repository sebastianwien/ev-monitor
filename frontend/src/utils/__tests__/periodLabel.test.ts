import { describe, it, expect } from 'vitest'
import { periodLabel, isoWeekNumber } from '../tripTimeFormat'

const TODAY = new Date(2026, 7, 26) // Mi, 26.8.2026

describe('isoWeekNumber', () => {
  it('nennt die Kalenderwoche zu ihrem Montag', () => {
    expect(isoWeekNumber('2026-08-24')).toBe(35)
    expect(isoWeekNumber('2026-01-05')).toBe(2)
  })

  it('schlaegt den Jahreswechsel der ISO-Regel zu', () => {
    // Die Woche ab Mo 29.12.2025 enthaelt den 1.1.2026 - sie ist KW 1, nicht KW 53.
    expect(isoWeekNumber('2025-12-29')).toBe(1)
  })

  it('ist null ohne verwertbaren Schluessel', () => {
    expect(isoWeekNumber('unknown')).toBeNull()
  })
})

describe('periodLabel', () => {
  it('beschriftet den Tag wie das Datumsband', () => {
    expect(periodLabel('2026-08-26', 'day', 'de', TODAY)).toBe('Heute')
    expect(periodLabel('2026-08-25', 'day', 'de', TODAY)).toBe('Gestern')
  })

  it('spannt die Woche von Montag bis Sonntag auf', () => {
    expect(periodLabel('2026-08-24', 'week', 'de', TODAY)).toBe('24.8. - 30.8.')
  })

  it('nennt den Monat beim Namen, in der Sprache des Nutzers', () => {
    expect(periodLabel('2026-08', 'month', 'de', TODAY)).toBe('August 2026')
    expect(periodLabel('2026-08', 'month', 'en', TODAY)).toBe('August 2026')
    expect(periodLabel('2026-08', 'month', 'sv', TODAY)).toContain('2026')
  })

  it('bleibt bei Fahrten ohne Zeitstempel leer - der Aufrufer setzt dort seinen Text', () => {
    expect(periodLabel('unknown', 'week', 'de', TODAY)).toBe('')
    expect(periodLabel('kaputt', 'month', 'de', TODAY)).toBe('')
  })
})

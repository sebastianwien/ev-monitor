import { describe, it, expect } from 'vitest'
import { formatPeriod } from '../formatPeriod'

describe('formatPeriod', () => {
  it('formatiert eine Range als MM/JJ–MM/JJ', () => {
    expect(formatPeriod('2020-11-01', '2023-08-31')).toBe('11/20–08/23')
  })

  it('formatiert offen endende Einträge als "seit MM/JJ"', () => {
    expect(formatPeriod('2025-12-01', null)).toBe('seit 12/25')
  })

  it('akzeptiert anderen since-Label', () => {
    expect(formatPeriod('2025-12-01', null, 'since')).toBe('since 12/25')
  })

  it('gibt null zurück wenn kein Datum', () => {
    expect(formatPeriod(null, null)).toBeNull()
  })

  it('führende Null beim Monat', () => {
    expect(formatPeriod('2022-01-01', '2024-03-31')).toBe('01/22–03/24')
  })

  it('gleicher Monat und Jahr in Range', () => {
    expect(formatPeriod('2021-05-01', '2021-12-31')).toBe('05/21–12/21')
  })
})

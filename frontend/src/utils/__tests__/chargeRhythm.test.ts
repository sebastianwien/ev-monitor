import { describe, it, expect } from 'vitest'
import { i18n } from '../../i18n'
import { chargeRhythm, chargeRhythmText, chargeRhythmSpanText, type RhythmFormat } from '../chargeRhythm'

const translate = (i18n.global as unknown as { t: (key: string, params: object, plural?: number) => string }).t
const de: RhythmFormat = {
  t: (key, params, plural) => translate(key, params ?? {}, plural as number),
  formatDecimal: (v, d) => new Intl.NumberFormat('de-DE', { minimumFractionDigits: d, maximumFractionDigits: d }).format(v),
}

describe('chargeRhythm', () => {
  it('over once a week counts charges with one decimal', () => {
    expect(chargeRhythm(2.34, true)).toEqual({ kind: 'times', value: 2.3 })
    expect(chargeRhythmText(2.3, true, de)).toBe('2,3× pro Woche')
    expect(chargeRhythmText(2.04, true, de)).toBe('2× pro Woche')
  })

  it('half to once a week counts days, rounded down', () => {
    // Anna: 40 km on 3 days, winter range 300 km: 120 / 180 = 0.67 → 7 / 0.67 = 10.4 → 10
    expect(chargeRhythmText(120 / 180, true, de)).toBe('alle 10 Tage')
    expect(chargeRhythmText(1, true, de)).toBe('alle 7 Tage')
    expect(chargeRhythmText(0.5, true, de)).toBe('alle 14 Tage')
  })

  it('below half a week counts weeks, at least two', () => {
    expect(chargeRhythmText(0.3, true, de)).toBe('alle 3 Wochen')
    expect(chargeRhythmText(0.49, true, de)).toBe('alle 2 Wochen')
  })

  it('without home charging counts fast-charge stops, rounded up', () => {
    expect(chargeRhythmText(1.22, false, de)).toBe('2 Ladestopps pro Woche')
    expect(chargeRhythmText(0.4, false, de)).toBe('1 Ladestopp pro Woche')
  })

  it('no commute needs no charging', () => {
    expect(chargeRhythmText(0, true, de)).toBe('kein Laden nötig')
  })

  describe('span from the largest to the smallest battery: always in days', () => {
    it('home charging: both ends as days, smaller number first', () => {
      expect(chargeRhythmSpanText(0.67, 1, true, de)).toBe('alle 7 bis 10 Tage')
      // 7 / 2.3 = 3, 7 / 1.2 = 5.8 → 5
      expect(chargeRhythmSpanText(1.2, 2.3, true, de)).toBe('alle 3 bis 5 Tage')
      // 7 / 0.4 = 17.5 → 17, 7 / 0.25 = 28
      expect(chargeRhythmSpanText(0.25, 0.4, true, de)).toBe('alle 17 bis 28 Tage')
    })

    it('ends across a threshold stay in one unit (ID.3: 0.49 and 0.85 a week)', () => {
      expect(chargeRhythmSpanText(0.49, 0.85, true, de)).toBe('alle 8 bis 14 Tage')
      expect(chargeRhythmSpanText(0.57, 1.01, true, de)).toBe('alle 6 bis 12 Tage')
    })

    it('equal after rounding: a single value', () => {
      expect(chargeRhythmSpanText(0.68, 0.7, true, de)).toBe('alle 10 Tage')
      expect(chargeRhythmSpanText(2.4, 2.6, true, de)).toBe('alle 2 Tage')
    })

    it('very often: at least every day', () => {
      expect(chargeRhythmSpanText(8, 10, true, de)).toBe('täglich')
    })

    it('without home charging: stops per week as before', () => {
      expect(chargeRhythmSpanText(0.8, 1.3, false, de)).toBe('1 bis 2 Ladestopps pro Woche')
      expect(chargeRhythmSpanText(0.2, 0.4, false, de)).toBe('1 Ladestopp pro Woche')
    })
  })
})

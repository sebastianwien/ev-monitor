/**
 * How often the reader plugs in, in words, from charges (or fast-charge stops) per week.
 * One table for the ranking and the model page; the backend result mail mirrors it
 * (ChargeRhythm.java):
 *
 * | per week          | text                                           |
 * |-------------------|------------------------------------------------|
 * | over 1            | "2,3× pro Woche", one decimal, ",0" dropped    |
 * | 0.5 to 1          | "alle 10 Tage", 7 / L rounded down             |
 * | below 0.5         | "alle 3 Wochen", 1 / L rounded down, at least 2 |
 * | no home charging  | "2 Ladestopps pro Woche", rounded up           |
 */

export type ChargeRhythm =
  | { kind: 'times' | 'days' | 'weeks' | 'stops'; value: number }
  | { kind: 'none' }

export interface RhythmFormat {
  t: (key: string, params?: Record<string, unknown>, plural?: number) => string
  formatDecimal: (value: number, decimals: number) => string
}

const DAYS_PER_WEEK = 7
const MIN_WEEKS = 2

export function chargeRhythm(perWeek: number, homeCharging: boolean): ChargeRhythm {
  if (!(perWeek > 0)) return { kind: 'none' }
  if (!homeCharging) return { kind: 'stops', value: Math.ceil(perWeek) }
  if (perWeek > 1) return { kind: 'times', value: Math.round(perWeek * 10) / 10 }
  if (perWeek >= 0.5) return { kind: 'days', value: Math.floor(DAYS_PER_WEEK / perWeek) }
  return { kind: 'weeks', value: Math.max(MIN_WEEKS, Math.floor(1 / perWeek)) }
}

function number(r: ChargeRhythm, fmt: RhythmFormat): string {
  if (r.kind === 'none') return ''
  return fmt.formatDecimal(r.value, Number.isInteger(r.value) ? 0 : 1)
}

function text(r: ChargeRhythm, fmt: RhythmFormat): string {
  if (r.kind === 'none') return fmt.t('charge_rhythm.none')
  return fmt.t(`charge_rhythm.${r.kind}`, { n: number(r, fmt) }, r.value)
}

export function chargeRhythmText(perWeek: number, homeCharging: boolean, fmt: RhythmFormat): string {
  return text(chargeRhythm(perWeek, homeCharging), fmt)
}

/**
 * Span over the battery variants, `low` from the largest battery, `high` from the smallest.
 * Same kind: one unit, smaller number first ("alle 7 bis 10 Tage"); different kinds: the more
 * frequent one first ("1,2× pro Woche bis alle 8 Tage").
 */
export function chargeRhythmSpanText(low: number, high: number, homeCharging: boolean, fmt: RhythmFormat): string {
  const often = chargeRhythm(Math.max(low, high), homeCharging)
  const rare = chargeRhythm(Math.min(low, high), homeCharging)
  const oftenText = text(often, fmt)
  const rareText = text(rare, fmt)
  if (oftenText === rareText) return oftenText
  if (often.kind !== rare.kind || often.kind === 'none' || rare.kind === 'none') {
    return fmt.t('charge_rhythm.mixed_span', { min: oftenText, max: rareText })
  }
  const [a, b] = [often.value, rare.value].sort((x, y) => x - y)
  return fmt.t(`charge_rhythm.${often.kind}_span`, {
    min: fmt.formatDecimal(a, Number.isInteger(a) ? 0 : 1),
    max: fmt.formatDecimal(b, Number.isInteger(b) ? 0 : 1),
  })
}

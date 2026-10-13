/**
 * How often the reader plugs in, in words, from charges (or fast-charge stops) per week.
 * One table for the ranking and the model page; the backend result mail mirrors it
 * (ChargeRhythm.java):
 *
 * | per week          | text                                           |
 * |-------------------|------------------------------------------------|
 * | 3.5 or more       | "täglich" (7 / L rounded down is 1)            |
 * | 0.5 to 3.5        | "alle 10 Tage", 7 / L rounded down             |
 * | below 0.5         | "alle 3 Wochen", 1 / L rounded down, at least 2 |
 * | no home charging  | "2 Ladestopps pro Woche", rounded up           |
 */

export type ChargeRhythm =
  | { kind: 'daily' | 'days' | 'weeks' | 'stops'; value: number }
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
  if (perWeek >= 0.5) {
    const days = daysBetween(perWeek)
    return days === 1 ? { kind: 'daily', value: 1 } : { kind: 'days', value: days }
  }
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

/** Whole days between charges, at least one (then "täglich") */
function daysBetween(perWeek: number): number {
  return Math.max(1, Math.floor(DAYS_PER_WEEK / perWeek))
}

/**
 * Span over the battery variants, `low` from the largest battery, `high` from the smallest.
 * Home charging: both ends always in days ("alle 8 bis 14 Tage"), so a span never mixes units
 * when the two batteries fall on different sides of a threshold of the table above. Without
 * home charging: stops per week ("1 bis 2 Ladestopps pro Woche").
 */
export function chargeRhythmSpanText(low: number, high: number, homeCharging: boolean, fmt: RhythmFormat): string {
  const often = Math.max(low, high)
  const rare = Math.min(low, high)
  const single = chargeRhythmText(often, homeCharging, fmt)
  if (single === chargeRhythmText(rare, homeCharging, fmt)) return single
  if (!homeCharging) {
    return fmt.t('charge_rhythm.stops_span', { min: Math.ceil(rare), max: Math.ceil(often) })
  }
  const min = daysBetween(often)
  const max = daysBetween(rare)
  if (min === max) return min === 1 ? fmt.t('charge_rhythm.daily') : fmt.t('charge_rhythm.days', { n: min }, min)
  return fmt.t('charge_rhythm.days_span', { min, max })
}

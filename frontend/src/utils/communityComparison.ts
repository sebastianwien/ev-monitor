/**
 * Einordnung eigener Werte gegen den Community-Schnitt der Modellgruppe.
 *
 * Gilt fuer Groessen, bei denen niedriger besser ist (Verbrauch kWh/100km, Kosten je kWh
 * oder je 100 km). Staffelung: mehr als 5 % unter dem Schnitt = besser (gruen), ±5 % =
 * vergleichbar (neutral), mehr als 5 % darueber = schlechter (amber), mehr als 30 %
 * darueber = deutlich schlechter (rot). Das ±5-%-Band gibt es, weil Wetter, Strecke und
 * Messrauschen ohnehin in dieser Groessenordnung liegen - eine Wertung waere
 * Scheingenauigkeit.
 */
export type ComparisonLevel = 'better' | 'similar' | 'worse' | 'much_worse'

const SIMILAR_BAND = 0.05
const MUCH_WORSE_THRESHOLD = 0.30

export function comparisonLevel(
  value: number | null | undefined,
  communityAvg: number | null | undefined,
): ComparisonLevel | null {
  if (value == null || !communityAvg) return null
  if (value < communityAvg * (1 - SIMILAR_BAND)) return 'better'
  if (value > communityAvg * (1 + MUCH_WORSE_THRESHOLD)) return 'much_worse'
  if (value > communityAvg * (1 + SIMILAR_BAND)) return 'worse'
  return 'similar'
}

/** Abweichung vom Community-Schnitt in ganzen Prozent, negativ = darunter. */
export function comparisonDeltaPercent(
  value: number | null | undefined,
  communityAvg: number | null | undefined,
): number | null {
  if (value == null || !communityAvg) return null
  return Math.round((value / communityAvg - 1) * 100)
}

/** Chip-Farbwelt je Stufe; ohne Einordnung neutral, wie die uebrigen Kennzahl-Chips. */
export function comparisonChipClass(level: ComparisonLevel | null): string {
  switch (level) {
    case 'better':
      return 'bg-emerald-50 dark:bg-emerald-900/20 border-emerald-300 dark:border-emerald-700/60 text-emerald-700 dark:text-emerald-300'
    case 'similar':
      return 'bg-gray-50 dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-slate-700 dark:text-gray-200'
    case 'worse':
      return 'bg-amber-50 dark:bg-amber-900/20 border-amber-300 dark:border-amber-700/60 text-amber-700 dark:text-amber-300'
    case 'much_worse':
      return 'bg-rose-50 dark:bg-rose-900/20 border-rose-300 dark:border-rose-700/60 text-rose-700 dark:text-rose-300'
    default:
      return 'bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-slate-700 dark:text-gray-200'
  }
}

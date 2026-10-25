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

/** Mindestzahl an Fahrern (App-CI) und Ladungen, ab der ein Community-Schnitt etwas sagt. */
const MIN_COMMUNITY_DRIVERS = 5
const MIN_COMMUNITY_LOGS = 5

/** Taugt der Modell-Schnitt als Vergleich? Erst ab 5 Fahrern, darunter ist es ein Einzelwert. */
export function communityStatsUsable(
  stats: { logCount?: number | null; uniqueContributors?: number | null } | null | undefined,
): boolean {
  return (stats?.logCount ?? 0) >= MIN_COMMUNITY_LOGS && (stats?.uniqueContributors ?? 0) >= MIN_COMMUNITY_DRIVERS
}

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

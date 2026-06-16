/**
 * Assumed average electricity price used to express phantom (idle) drain as a cost.
 * This is a flat assumption, not a per-user rate - originally introduced inline in the
 * Insights donut. A future improvement could derive it from the user's own charging costs.
 */
export const PHANTOM_EUR_PER_KWH = 0.29

interface DrainEntry {
  _phantomDrain?: { kwh?: number | null } | null
}

/** Total idle-drain energy (kWh) across the given feed entries. */
export function sumPhantomKwh(entries: readonly DrainEntry[] | null | undefined): number {
  return (entries ?? []).reduce((sum, e) => sum + (e?._phantomDrain?.kwh ?? 0), 0)
}

/** Express an idle-drain energy amount (kWh) as an assumed cost in EUR. */
export function phantomEur(kwh: number): number {
  return kwh * PHANTOM_EUR_PER_KWH
}

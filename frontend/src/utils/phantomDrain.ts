/**
 * Assumed average electricity price used to express phantom (idle) drain as a cost.
 * This is a flat assumption, not a per-user rate - originally introduced inline in the
 * Insights donut. A future improvement could derive it from the user's own charging costs.
 */
export const PHANTOM_EUR_PER_KWH = 0.29

/** Minimum drain (kWh) for a parked gap to count as a real idle drain rather than noise. */
export const PHANTOM_MIN_KWH = 0.05

export interface PhantomTeaser {
  count: number
  latestKwh: number
  totalEur: number
}

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

/**
 * Build the locked-state teaser summary from a time-descending feed (e.g. mergedLogFeed):
 * the most-recent drain as a sample plus the count and total assumed cost. Returns null
 * when there is no real drain to tease. Entitlement gating is the caller's concern.
 */
export function phantomTeaser(
  entries: readonly DrainEntry[] | null | undefined,
): PhantomTeaser | null {
  const withDrain = (entries ?? []).filter((e) => (e?._phantomDrain?.kwh ?? 0) > PHANTOM_MIN_KWH)
  if (withDrain.length === 0) return null
  const totalKwh = withDrain.reduce((sum, e) => sum + (e._phantomDrain!.kwh ?? 0), 0)
  return {
    count: withDrain.length,
    // mergedLogFeed is sorted descending, so the first match is the most recent drain.
    latestKwh: withDrain[0]._phantomDrain!.kwh ?? 0,
    totalEur: phantomEur(totalKwh),
  }
}

/**
 * Pure helpers of the compare page. The figures themselves come from the model ranking
 * (useModelRanking), the page only marks and words them.
 */

/**
 * Which models are better on a figure: those sharing the best value while at least one model is
 * behind. A tie marks nothing, a missing value is left out. Pass the values as the page shows
 * them (rounded), so two figures that read alike never count as different.
 */
export function betterMarks(values: (number | null)[], lowerIsBetter: boolean): boolean[] {
  const present = values.filter((v): v is number => v != null)
  if (present.length < 2) return values.map(() => false)
  const best = lowerIsBetter ? Math.min(...present) : Math.max(...present)
  if (present.every(v => v === best)) return values.map(() => false)
  return values.map(v => v === best)
}

/** The compared models as one Plausible property: keys sorted by code point (same in every browser) and joined, so A,B and B,A are one set. */
export function compareSetKey(keys: string[]): string {
  return [...keys].sort().join(',')
}

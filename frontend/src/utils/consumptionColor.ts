/**
 * Returns Tailwind classes for a consumption badge based on how the value
 * compares to the car's average consumption (relative delta).
 *
 * Green  = better than average (lower consumption)
 * Gray   = around average (±5%)
 * Amber  = worse than average
 * Red    = significantly worse
 *
 * Falls back to neutral gray when no average is available.
 */
export function consumptionBadgeClass(
  consumptionKwhPer100km: number,
  avgKwhPer100km: number | null
): string {
  if (avgKwhPer100km == null || avgKwhPer100km === 0) {
    return 'bg-gray-100 border-gray-200 text-gray-600'
  }

  const deltaPercent = ((consumptionKwhPer100km - avgKwhPer100km) / avgKwhPer100km) * 100

  if (deltaPercent <= -15) return 'bg-emerald-100 border-emerald-300 text-emerald-800'
  if (deltaPercent <= -5)  return 'bg-green-50 border-green-200 text-green-700'
  if (deltaPercent <= 5)   return 'bg-gray-100 border-gray-200 text-gray-600'
  if (deltaPercent <= 15)  return 'bg-amber-100 border-amber-300 text-amber-800'
  return                          'bg-red-100 border-red-300 text-red-700'
}

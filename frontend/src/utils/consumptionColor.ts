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
    return 'bg-gray-100 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300'
  }

  const deltaPercent = ((consumptionKwhPer100km - avgKwhPer100km) / avgKwhPer100km) * 100

  if (deltaPercent <= -15) return 'bg-emerald-100 dark:bg-emerald-900/40 border-emerald-300 dark:border-emerald-700 text-emerald-800 dark:text-emerald-200'
  if (deltaPercent <= -5)  return 'bg-green-50 dark:bg-green-900/30 border-green-200 dark:border-green-700 text-green-700 dark:text-green-300'
  if (deltaPercent <= 5)   return 'bg-gray-100 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300'
  if (deltaPercent <= 15)  return 'bg-amber-100 dark:bg-amber-900/40 border-amber-300 dark:border-amber-700 text-amber-800 dark:text-amber-200'
  return                          'bg-red-100 dark:bg-red-900/40 border-red-300 dark:border-red-700 text-red-700 dark:text-red-300'
}

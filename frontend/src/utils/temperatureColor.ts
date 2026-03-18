/**
 * Returns Tailwind classes for a temperature badge based on the value.
 * Deep blue (very cold) → cyan → green → amber → deep red (very hot)
 */
export function tempBadgeClass(celsius: number): string {
  if (celsius < -10) return 'bg-blue-900 border-blue-800 text-blue-100'
  if (celsius < 0)   return 'bg-blue-600 border-blue-500 text-white'
  if (celsius < 5)   return 'bg-blue-200 dark:bg-blue-900/50 border-blue-300 dark:border-blue-700 text-blue-900 dark:text-blue-100'
  if (celsius < 15)  return 'bg-cyan-100 dark:bg-cyan-900/40 border-cyan-200 dark:border-cyan-700 text-cyan-800 dark:text-cyan-200'
  if (celsius < 25)  return 'bg-emerald-100 dark:bg-emerald-900/40 border-emerald-200 dark:border-emerald-700 text-emerald-800 dark:text-emerald-200'
  if (celsius < 30)  return 'bg-amber-200 dark:bg-amber-900/50 border-amber-300 dark:border-amber-700 text-amber-900 dark:text-amber-100'
  if (celsius < 35)  return 'bg-orange-400 border-orange-500 text-white'
  return                    'bg-red-600 border-red-700 text-white'
}

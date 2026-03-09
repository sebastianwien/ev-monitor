/**
 * Returns Tailwind classes for a temperature badge based on the value.
 * Deep blue (very cold) → cyan → green → amber → deep red (very hot)
 */
export function tempBadgeClass(celsius: number): string {
  if (celsius < -10) return 'bg-blue-900 border-blue-800 text-blue-100'
  if (celsius < 0)   return 'bg-blue-600 border-blue-500 text-white'
  if (celsius < 5)   return 'bg-blue-200 border-blue-300 text-blue-900'
  if (celsius < 15)  return 'bg-cyan-100 border-cyan-200 text-cyan-800'
  if (celsius < 25)  return 'bg-emerald-100 border-emerald-200 text-emerald-800'
  if (celsius < 30)  return 'bg-amber-200 border-amber-300 text-amber-900'
  if (celsius < 35)  return 'bg-orange-400 border-orange-500 text-white'
  return                    'bg-red-600 border-red-700 text-white'
}

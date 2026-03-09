/**
 * Returns Tailwind classes for a cost-per-kWh badge.
 *
 * Gold shimmer = excellent (< €0.20/kWh)
 * Green        = good      (€0.20 – €0.40/kWh)
 * Yellow       = ok        (€0.41 – €0.60/kWh)
 * Red          = expensive (€0.61 – €0.80/kWh)
 * Dark red     = very exp. (> €0.80/kWh)
 *
 * Returns null when cost or kWh is missing/zero.
 */
export function costBadgeClass(costEur: number | null, kwhCharged: number | null): string | null {
  if (costEur == null || kwhCharged == null || kwhCharged === 0) return null

  const perKwh = costEur / kwhCharged

  if (perKwh < 0.20)  return 'cost-badge-gold'
  if (perKwh <= 0.40) return 'bg-green-100 border-green-300 text-green-800'
  if (perKwh <= 0.60) return 'bg-yellow-100 border-yellow-300 text-yellow-800'
  if (perKwh <= 0.80) return 'bg-red-100 border-red-300 text-red-700'
  return                     'bg-red-200 border-red-500 text-red-900'
}

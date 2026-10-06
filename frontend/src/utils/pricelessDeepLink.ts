import type { LocationQuery } from 'vue-router'

/**
 * Einstieg aus der Monatsrückblick-Mail: /logs?car=<id>&nachtragen=preis öffnet das Modal
 * "Preise nachtragen". Ein Auto, das nicht in der eigenen Liste steht, wird ignoriert; dann
 * bleibt das gerade gewählte Auto.
 */
export function pricelessDeepLink(
  query: LocationQuery,
  cars: readonly { id: string }[],
): { carId: string | null } | null {
  if (query.nachtragen !== 'preis') return null
  const car = typeof query.car === 'string' ? query.car : null
  return { carId: car && cars.some((c) => c.id === car) ? car : null }
}

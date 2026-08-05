/**
 * Hilfsfunktionen fuer die Ladekurve: Zuordnung einer Zeigerposition zum naechsten
 * Messpunkt und die gemeinsame Dauer-Formatierung von X-Achse und Scrub-Tooltip.
 */

/**
 * Index des Punktes, dessen x-Koordinate am naechsten an {@code x} liegt.
 * Erwartet aufsteigend sortierte x-Werte (die Kurve ist nach Zeit sortiert).
 * Liefert -1 bei leerer Eingabe.
 */
export function nearestIndexByX(xs: number[], x: number): number {
  if (xs.length === 0) return -1
  if (x <= xs[0]) return 0
  if (x >= xs[xs.length - 1]) return xs.length - 1

  let lo = 0
  let hi = xs.length - 1
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1
    if (xs[mid] <= x) lo = mid
    else hi = mid
  }
  return x - xs[lo] <= xs[hi] - x ? lo : hi
}

/** Verstrichene Dauer als "45 Min", "2 h" oder "1h 20". */
export function formatDuration(ms: number): string {
  const totalMin = Math.round(ms / 60000)
  if (totalMin < 60) return `${totalMin} Min`
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return m === 0 ? `${h} h` : `${h}h ${m}`
}

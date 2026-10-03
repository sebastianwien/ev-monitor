/**
 * True, wenn der Nutzer weniger Bewegung oder weniger Daten wuenscht.
 * Beides schaltet Count-ups, Partikel und Reveal-Animationen ab.
 * Ohne window (Tests, SSR) immer false.
 */
export function prefersReducedEffects(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    || window.matchMedia('(prefers-reduced-data: reduce)').matches
}

const MAX = 256
const MIN = 120
const GAP = 8
/** Unter dieser Höhe gilt "darunter" als verdeckt, etwa von der Tastatur */
const ENOUGH = 200

/**
 * Wo die Vorschlagsliste eines Felds aufgeht: darunter, solange dort Platz ist; sonst darüber,
 * wenn oben mehr Platz ist. Auf dem Handy verdeckt die Tastatur sonst die Liste.
 * `viewport` ist der sichtbare Ausschnitt (visualViewport: ohne Tastatur).
 */
export function listPlacement(field: { top: number; bottom: number }, viewport: { top: number; bottom: number }) {
  const below = viewport.bottom - field.bottom - GAP
  const aboveSpace = field.top - viewport.top - GAP
  const above = below < ENOUGH && aboveSpace > below
  return { above, maxHeight: Math.max(MIN, Math.min(MAX, above ? aboveSpace : below)) }
}

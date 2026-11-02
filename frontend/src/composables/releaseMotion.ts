/**
 * Bewegung nach dem Loslassen, gemeinsam für Sheets, Neuigkeiten-Karten und den Seiten-Pager.
 *
 * Wie native Karten: was der Finger losgelassen hat, läuft mit seinem Tempo weiter und bremst
 * bis zum Ziel ab, statt mit fester Dauer neu anzufahren oder auszublenden. RELEASE_EASE beginnt
 * mit dreifacher Durchschnittsgeschwindigkeit (Steigung y1/x1 = 3) und endet weich; die Dauer
 * wählt releaseMs so, dass diese Anfangsgeschwindigkeit dem Finger entspricht.
 */
export const RELEASE_EASE = 'cubic-bezier(0.25, 0.75, 0.35, 1)'
export const RELEASE_START_SLOPE = 3
export const RELEASE_MIN_MS = 140
export const RELEASE_MAX_MS = 320

const clamp = (ms: number) => Math.round(Math.min(RELEASE_MAX_MS, Math.max(RELEASE_MIN_MS, ms)))

/** Dauer für den Restweg (px) bei Fingertempo (px/ms), damit die Kurve mit dem Finger beginnt. */
export function releaseMs(distance: number, speed: number): number {
  const d = Math.abs(distance)
  const v = Math.abs(speed)
  if (d === 0) return RELEASE_MIN_MS
  if (v === 0) return RELEASE_MAX_MS
  return clamp(RELEASE_START_SLOPE * d / v)
}

/** Zurückfedern ohne Schwung: kurzer Weg kurz, langer Weg länger. */
export function settleMs(distance: number): number {
  return clamp(120 + Math.abs(distance) * 0.8)
}

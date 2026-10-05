/**
 * Geometrie der klebenden Zeitraum-Koepfe im Log-Feed. Der Kopf steht als erstes Kind in
 * seiner Gruppe; liegt der Gruppenanfang ueber der Klebelinie, ist der Kopf aus seiner
 * natuerlichen Lage verschoben - er klebt (oder wird gerade vom Gruppenende hinausgeschoben).
 *
 * Alle Werte sind Viewport-Koordinaten in px, wie getBoundingClientRect sie liefert.
 * `groupTop` meint die Ruhelage des Kopfes: den Gruppenanfang innerhalb des Rahmens.
 */

/** Halber Pixel Toleranz: Subpixel-Layout soll einen ruhenden Kopf nicht als klebend melden. */
const EPSILON_PX = 0.5

export function isGroupScrolledPast(groupTop: number, stickyTop: number): boolean {
  return groupTop < stickyTop - EPSILON_PX
}

/**
 * Wohin gescrollt werden muss, damit ein klebender Kopf beim Zuklappen an seiner Stelle
 * bleibt: der Gruppenanfang landet auf der Klebelinie. Ohne das schrumpft die Gruppe unter
 * dem Finger weg und man steht mitten in einer fremden Gruppe. null = nichts zu tun.
 */
export function collapseAnchorScrollY(scrollY: number, groupTop: number, stickyTop: number): number | null {
  if (!isGroupScrolledPast(groupTop, stickyTop)) return null
  return Math.round(scrollY + groupTop - stickyTop)
}

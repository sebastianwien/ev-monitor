/**
 * Höhen des Sheets über der Säulenkarte: klein (Kopf und zwei Zeilen), halb, ganz.
 * Das Sheet ist nicht modal, die Karte darüber bleibt bedienbar.
 */
export type Snap = 'peek' | 'half' | 'full'
export type SnapHeights = Record<Snap, number>

const ORDER: Snap[] = ['peek', 'half', 'full']
/** Ab diesem Tempo (px/ms) zählt ein Zug als Wisch in seine Richtung, unabhängig vom Weg */
const FLICK = 0.5
/** Oben bleibt ein Streifen Karte sichtbar, damit klar ist, dass darunter die Karte liegt */
const MAP_STRIP = 56

export function snapHeights(container: number, peek: number): SnapHeights {
  const full = Math.max(peek, container - MAP_STRIP)
  const half = Math.min(full, Math.max(peek, Math.round(container / 2)))
  return { peek, half, full }
}

/**
 * Zielhöhe nach dem Loslassen. `velocity` positiv heißt nach oben (das Sheet wächst).
 * Ein Wisch geht zur nächsten Höhe in seiner Richtung, sonst gilt die nächstgelegene.
 */
export function snapAfterDrag(h: SnapHeights, height: number, velocity: number): Snap {
  if (Math.abs(velocity) >= FLICK) {
    if (velocity > 0) return ORDER.find(s => h[s] > height + 1) ?? 'full'
    return [...ORDER].reverse().find(s => h[s] < height - 1) ?? 'peek'
  }
  return ORDER.reduce((best, s) => Math.abs(h[s] - height) < Math.abs(h[best] - height) ? s : best, 'peek' as Snap)
}

export function nextOnTap(s: Snap): Snap {
  return s === 'peek' ? 'half' : s === 'half' ? 'full' : 'peek'
}

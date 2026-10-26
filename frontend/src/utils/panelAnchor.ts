/** Abstand der Kopfzeile im Panel zu seinem Außenrand: p-2 plus 2 px Kante. */
export const PANEL_INSET = 10
const MARGIN = 8

export interface PanelAnchor { top: number; left: number; width: number; shift: number }

/**
 * Lage eines Panels, das sich deckungsgleich über seinen Auslöser legt (AnchoredPanel). align left: die Kopfzeile
 * beginnt am Auslöser, right: sie endet dort. Am Bildschirmrand rückt das Panel nach innen, shift schiebt die
 * Kopfzeile darin zurück auf den Auslöser.
 */
export function panelAnchor(rect: { top: number; left: number; right: number }, viewportWidth: number,
  opts: { width: number; align: 'left' | 'right' }): PanelAnchor {
  const width = Math.min(opts.width, viewportWidth - 2 * MARGIN)
  const ideal = opts.align === 'left' ? rect.left - PANEL_INSET : rect.right + PANEL_INSET - width
  const left = Math.min(Math.max(MARGIN, ideal), viewportWidth - MARGIN - width)
  return { top: Math.max(MARGIN, rect.top - PANEL_INSET), left, width, shift: ideal - left }
}

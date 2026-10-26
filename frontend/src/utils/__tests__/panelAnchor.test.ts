import { describe, it, expect } from 'vitest'
import { panelAnchor, PANEL_INSET } from '../panelAnchor'

describe('panelAnchor', () => {
  it('legt die Kopfzeile links bündig auf den Auslöser', () => {
    const a = panelAnchor({ top: 152, left: 99, right: 260 }, 1280, { width: 448, align: 'left' })
    expect(a).toEqual({ top: 152 - PANEL_INSET, left: 99 - PANEL_INSET, width: 448, shift: 0 })
  })

  it('am Bildschirmrand rückt das Panel nach innen, shift schiebt die Kopfzeile zurück', () => {
    const a = panelAnchor({ top: 118, left: 8, right: 160 }, 390, { width: 448, align: 'left' })
    expect(a.left).toBe(8)
    expect(a.width).toBe(374)
    expect(a.shift).toBe(-10)
  })

  it('rechtsbündig endet die Kopfzeile am Auslöser', () => {
    const a = panelAnchor({ top: 150, left: 900, right: 1150 }, 1280, { width: 320, align: 'right' })
    expect(a.left + a.width).toBe(1150 + PANEL_INSET)
    expect(a.shift).toBe(0)
  })
})

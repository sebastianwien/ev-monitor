import { describe, it, expect } from 'vitest'
import { isKeyboardOpen, KEYBOARD_MIN_DELTA_PX } from '../useKeyboardOpen'

describe('useKeyboardOpen - reine Erkennungs-Logik', () => {
  it('erkennt die Tastatur als offen, wenn der Visual-Viewport deutlich kleiner ist als das Layout', () => {
    // Layout 780, Visual 480 -> 300px geschluckt = Tastatur
    expect(isKeyboardOpen(780, 480)).toBe(true)
  })

  it('erkennt sie als offen exakt oberhalb der Schwelle', () => {
    expect(isKeyboardOpen(780, 780 - KEYBOARD_MIN_DELTA_PX - 1)).toBe(true)
  })

  it('erkennt sie NICHT als offen bei kleinen Schwankungen (URL-Bar, Toolbars)', () => {
    expect(isKeyboardOpen(780, 780)).toBe(false)
    expect(isKeyboardOpen(780, 780 - KEYBOARD_MIN_DELTA_PX + 1)).toBe(false)
  })

  it('ist robust gegen 0/negative Werte (kein false positive)', () => {
    expect(isKeyboardOpen(0, 0)).toBe(false)
    expect(isKeyboardOpen(780, 0)).toBe(false)
  })
})

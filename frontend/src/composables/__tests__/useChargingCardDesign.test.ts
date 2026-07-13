import { describe, it, expect } from 'vitest'
import { cardDesign, cardContainerStyle, cardChipStyle, cardTextColor, cardSubTextColor } from '../useChargingCardDesign'

describe('useChargingCardDesign', () => {
  it('gleiche Karte -> immer dasselbe Design (Log-Formular und Ladekarten-View zeigen dieselbe Kachel)', () => {
    const id = 'a3f1c9e2-1234-4a5b-8c9d-0e1f2a3b4c5d'
    expect(cardDesign(id)).toBe(cardDesign(id))
    expect(cardContainerStyle(id)).toEqual(cardContainerStyle(id))
  })

  it('verschiedene Karten verteilen sich ueber alle Designs', () => {
    const designs = new Set(Array.from({ length: 40 }, (_, i) => cardDesign(`card-${i}`)))
    expect(designs).toEqual(new Set(['stripe', 'circles', 'solid', 'pastel']))
  })

  it('jedes Design liefert Hintergrund, Chip und lesbare Textfarben', () => {
    for (let i = 0; i < 40; i++) {
      const id = `card-${i}`
      expect(cardContainerStyle(id).background).toBeTruthy()
      expect(cardContainerStyle(id)['--btn-shadow-color']).toBeTruthy()
      expect(cardChipStyle(id).background).toBeTruthy()
      expect(cardTextColor(id)).toBeTruthy()
      expect(cardSubTextColor(id)).toBeTruthy()
    }
  })
})

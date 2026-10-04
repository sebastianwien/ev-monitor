import { describe, it, expect } from 'vitest'
import { listPlacement } from '../listPlacement'

/** Vorschläge über dem Feld, wenn darunter (Tastatur) kein Platz ist */
describe('listPlacement', () => {
  it('genug Platz darunter: unten, höchstens 256 px', () => {
    expect(listPlacement({ top: 100, bottom: 140 }, { top: 0, bottom: 800 })).toEqual({ above: false, maxHeight: 256 })
  })
  it('Tastatur offen, unten kaum Platz, oben mehr: oben, so hoch wie Platz ist', () => {
    expect(listPlacement({ top: 300, bottom: 340 }, { top: 0, bottom: 450 })).toEqual({ above: true, maxHeight: 256 })
    expect(listPlacement({ top: 200, bottom: 240 }, { top: 0, bottom: 350 })).toEqual({ above: true, maxHeight: 192 })
  })
  it('oben noch weniger Platz als unten: unten bleiben', () => {
    expect(listPlacement({ top: 60, bottom: 100 }, { top: 0, bottom: 250 })).toEqual({ above: false, maxHeight: 142 })
  })
  it('nie kleiner als 120 px', () => {
    expect(listPlacement({ top: 20, bottom: 60 }, { top: 0, bottom: 100 }).maxHeight).toBe(120)
  })
})

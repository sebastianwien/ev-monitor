import { describe, it, expect } from 'vitest'
import { numberParts } from '../numberParts'

const bold = (text: string) => numberParts(text).filter(p => p.number).map(p => p.text)

describe('numberParts', () => {
  it('markiert Zahlen mit Tausenderpunkt, Dezimalkomma und Prozent', () => {
    expect(bold('44.617 kWh in 2.758 Ladevorgängen geladen')).toEqual(['44.617', '2.758'])
    expect(bold('25.795 € gespart (Benzin 2,24 €/L)')).toEqual(['25.795', '2,24'])
    expect(bold('63% aller Ladevorgänge zu Hause')).toEqual(['63%'])
  })

  it('lässt Ziffern in Wörtern wie CO2 stehen', () => {
    expect(bold('1.200 kg CO2 gespart')).toEqual(['1.200'])
  })

  it('setzt den Text unverändert wieder zusammen', () => {
    const text = 'Beliebtester Anbieter: EnBW mit 51 Ladevorgängen'
    expect(numberParts(text).map(p => p.text).join('')).toBe(text)
  })

  it('liefert ohne Zahl ein einziges Textstück', () => {
    expect(numberParts('Ohne Zahl')).toEqual([{ text: 'Ohne Zahl', number: false }])
  })
})

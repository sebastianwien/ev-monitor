import { describe, it, expect } from 'vitest'
import { numberParts } from '../numberParts'

const bold = (text: string) => numberParts(text).filter(p => p.number).map(p => p.text)

describe('numberParts', () => {
  it('markiert Zahlen mit Tausenderpunkt, Dezimalkomma und Prozent', () => {
    expect(bold('44.617 kWh in 2.758 Ladevorgängen geladen')).toEqual(['44.617', '2.758'])
    expect(bold('25.795 € gespart (Benzin 2,24 €/L)')).toEqual(['25.795 €', '2,24 €'])
    expect(bold('63% aller Ladevorgänge zu Hause')).toEqual(['63%'])
  })

  it('nimmt ein Minus vor der Zahl mit, aber keinen Bindestrich zwischen Zahlen', () => {
    expect(bold('Die nächsten 7 Tage: -1 bis 6 °C')).toEqual(['7', '-1', '6'])
    expect(bold('2-3 Ladungen')).toEqual(['2', '3'])
  })

  it('lässt Ziffern in Wörtern wie CO2 stehen', () => {
    expect(bold('1.200 kg CO2 gespart')).toEqual(['1.200'])
  })

  it('setzt den Text unverändert wieder zusammen', () => {
    const text = 'Beliebtester Anbieter: EnBW mit 51 Ladevorgängen'
    expect(numberParts(text).map(p => p.text).join('')).toBe(text)
  })

  it('setzt auf Wunsch die Einheit mit fett, auch "kg CO₂"', () => {
    const withUnits = (text: string) => numberParts(text, { units: true }).filter(p => p.number).map(p => p.text)
    expect(withUnits('hast du 45\u00a0kg\u00a0CO₂ und 7\u00a0€ gespart')).toEqual(['45\u00a0kg\u00a0CO₂', '7\u00a0€'])
    expect(withUnits('you saved 45\u00a0kg of CO₂ and 7\u00a0€')).toEqual(['45\u00a0kg', '7\u00a0€'])
    expect(withUnits('spart 45\u00a0kg\u00a0CO₂ og 70\u00a0kr')).toEqual(['45\u00a0kg\u00a0CO₂', '70\u00a0kr'])
    expect(bold('hast du 45\u00a0kg\u00a0CO₂ gespart')).toEqual(['45'])
  })

  it('liefert ohne Zahl ein einziges Textstück', () => {
    expect(numberParts('Ohne Zahl')).toEqual([{ text: 'Ohne Zahl', number: false }])
  })

  it('markiert Geldbeträge (€ oder ct davor oder dahinter), sonst nicht', () => {
    const money = (text: string, units = false) => numberParts(text, { units }).filter(p => p.money).map(p => p.text)
    expect(money('151\u00a0€ für 11 Ladungen.')).toEqual(['151\u00a0€'])
    expect(money('151\u00a0€ für 11 Ladungen.', true)).toEqual(['151\u00a0€'])
    expect(money('€151 for 11 charges')).toEqual(['€151'])
    expect(money('€151 for 11 charges', true)).toEqual(['€151'])
    expect(money('Preis 39,5 ct je kWh, 45 kWh geladen')).toEqual(['39,5 ct'])
    expect(money('472 km mit 17,2 kWh')).toEqual([])
    expect(money('Für 5 € 3 Stopps')).toEqual(['5 €'])
  })
})

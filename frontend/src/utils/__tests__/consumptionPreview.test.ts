import { describe, it, expect } from 'vitest'
import { consumptionPreview } from '../consumptionPreview'

const base = { kwhCharged: 30, kwhAtVehicle: null, chargingType: 'AC' as const, odometerKm: 10_300, socAfter: null, capacityKwh: 60, costEur: 12, previous: { odometerKm: 10_100, socAfter: null } }

describe('consumptionPreview (Richtwert, Spiegel der Backend-Regel)', () => {
  it('Brutto an der Säule zählt ohne Wirkungsgrad (Entscheidung 01.10.2026), Distanz ist die Tacho-Differenz', () => {
    const p = consumptionPreview(base)!
    expect(p.kwhPer100km).toBeCloseTo(30 / 200 * 100, 5)
    expect(p.eurPer100km).toBeCloseTo(6, 5)
    expect(p.plausible).toBe(true)
  })
  it('Netto laut Auto hat Vorrang und wird nicht korrigiert', () => {
    expect(consumptionPreview({ ...base, kwhAtVehicle: 25 })!.kwhPer100km).toBeCloseTo(12.5, 5)
  })
  it('DC rechnet genauso brutto wie AC', () => {
    expect(consumptionPreview({ ...base, chargingType: 'DC' })!.kwhPer100km).toBeCloseTo(15, 5)
  })
  it('SoC-Differenz zum Vorgänger korrigiert die Energie über die Kapazität', () => {
    const p = consumptionPreview({ ...base, socAfter: 80, previous: { odometerKm: 10_100, socAfter: 90 } })!
    expect(p.kwhPer100km).toBeCloseTo((30 + 6) / 2, 5)
  })
  it('ohne Vorgänger, unter Mindestdistanz oder ohne Energie gibt es keinen Richtwert', () => {
    expect(consumptionPreview({ ...base, previous: null })).toBeNull()
    expect(consumptionPreview({ ...base, odometerKm: 10_105 })).toBeNull()
    expect(consumptionPreview({ ...base, kwhCharged: null })).toBeNull()
  })
  it('außerhalb 10 bis 40 kWh/100 km ist der Wert unplausibel, Kosten ohne Betrag bleiben null', () => {
    const p = consumptionPreview({ ...base, kwhCharged: 120, costEur: null })!
    expect(p.plausible).toBe(false)
    expect(p.eurPer100km).toBeNull()
  })
})

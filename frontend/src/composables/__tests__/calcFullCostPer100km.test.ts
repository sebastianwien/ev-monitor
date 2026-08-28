import { describe, it, expect } from 'vitest'
import { calcFullCostPer100km } from '../useDashboardStats'

describe('calcFullCostPer100km', () => {
  it('addiert die Fixkosten-Umlage auf die Energiekosten', () => {
    // 9.57 EUR/100km Energie + 300 EUR Fixkosten auf 2000 km = 15 EUR/100km
    expect(calcFullCostPer100km(9.57, 300, 0, 2000)).toBeCloseTo(24.57, 2)
  })

  it('zieht Einnahmen von der Umlage ab', () => {
    // 300 EUR Fixkosten - 100 EUR Einnahmen = 200 EUR auf 2000 km = 10 EUR/100km
    expect(calcFullCostPer100km(9.57, 300, 100, 2000)).toBeCloseTo(19.57, 2)
  })

  it('kann unter die Energiekosten fallen, wenn die Einnahmen ueberwiegen', () => {
    expect(calcFullCostPer100km(9.57, 100, 300, 2000)).toBeCloseTo(-0.43, 2)
  })

  it('entspricht den Energiekosten, wenn es keine Fixkosten gibt', () => {
    expect(calcFullCostPer100km(9.57, 0, 0, 2000)).toBeCloseTo(9.57, 2)
  })

  it('gibt null zurueck ohne Energiekosten-Basis', () => {
    expect(calcFullCostPer100km(null, 300, 0, 2000)).toBeNull()
  })

  it('gibt null zurueck ohne belastbare Distanz', () => {
    expect(calcFullCostPer100km(9.57, 300, 0, null)).toBeNull()
    expect(calcFullCostPer100km(9.57, 300, 0, 0)).toBeNull()
  })

  it('behandelt fehlende Fixkosten wie null', () => {
    expect(calcFullCostPer100km(9.57, null, null, 2000)).toBeCloseTo(9.57, 2)
  })
})

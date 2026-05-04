import { describe, it, expect } from 'vitest'
import { calcCostPer100km } from '../useDashboardStats'

describe('calcCostPer100km', () => {
  it('calderoni-Fall: beide Ladungen in Kosten, nur eine mit Distanz -> korrekt 9.57', () => {
    // Bug: totalCostEur/totalDistanceKm*100 ergab 22.15 wegen Scope-Mismatch
    // Korrekt: avgCostPerKwh(0.52) * avgConsumption(18.4) = 9.568
    const result = calcCostPer100km(0.52, 18.4)
    expect(result).toBeCloseTo(9.57, 1)
  })

  it('Ihle-Fall: viele alte Logs ohne Tacho -> korrekt 5.94', () => {
    const result = calcCostPer100km(0.28, 21.2)
    expect(result).toBeCloseTo(5.94, 1)
  })

  it('gibt null zurück wenn avgCostPerKwh null ist', () => {
    expect(calcCostPer100km(null, 18.4)).toBeNull()
  })

  it('gibt null zurück wenn avgConsumption null ist', () => {
    expect(calcCostPer100km(0.52, null)).toBeNull()
  })

  it('gibt null zurück wenn beide null sind', () => {
    expect(calcCostPer100km(null, null)).toBeNull()
  })

  it('gibt 0 zurück wenn Kosten 0 sind (Gratis-Laden)', () => {
    expect(calcCostPer100km(0, 18.4)).toBe(0)
  })
})

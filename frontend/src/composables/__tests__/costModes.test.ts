import { describe, it, expect } from 'vitest'
import {
  nextCostMode,
  resolveCostMode,
  calcFixedCostPer100km,
  calcFixedCostPerMonth,
  monthsInRange,
} from '../useDashboardStats'

describe('nextCostMode', () => {
  it('rotiert Energie -> Fixkosten -> Gesamt -> Energie', () => {
    expect(nextCostMode('energy')).toBe('fixed')
    expect(nextCostMode('fixed')).toBe('total')
    expect(nextCostMode('total')).toBe('energy')
  })
})

describe('resolveCostMode', () => {
  it('behaelt den gewaehlten Modus, solange Fixkostendaten vorliegen', () => {
    expect(resolveCostMode('fixed', true)).toBe('fixed')
    expect(resolveCostMode('total', true)).toBe('total')
    expect(resolveCostMode('energy', true)).toBe('energy')
  })

  it('faellt ohne Fixkostendaten auf Energie zurueck, statt mit 0,00 haengen zu bleiben', () => {
    // Regression: Wechsel auf einen Zeitraum ohne Fixkosten blendet den Umschalter aus -
    // die Kachel darf dann nicht im Fixkosten-/Gesamt-Modus mit 0,00-Werten steckenbleiben.
    expect(resolveCostMode('fixed', false)).toBe('energy')
    expect(resolveCostMode('total', false)).toBe('energy')
  })

  it('laesst Energie unveraendert, auch ohne Fixkostendaten', () => {
    expect(resolveCostMode('energy', false)).toBe('energy')
  })
})

describe('calcFixedCostPer100km', () => {
  it('legt die Netto-Fixkosten auf die Strecke um', () => {
    // (267 - 100) / 2000 km * 100 = 8,35
    expect(calcFixedCostPer100km(267, 100, 2000)).toBeCloseTo(8.35, 2)
  })

  it('wird negativ, wenn die Einnahmen ueberwiegen', () => {
    expect(calcFixedCostPer100km(100, 300, 2000)).toBeCloseTo(-10, 2)
  })

  it('gibt null zurueck ohne belastbare Distanz', () => {
    expect(calcFixedCostPer100km(267, 100, null)).toBeNull()
    expect(calcFixedCostPer100km(267, 100, 0)).toBeNull()
  })

  it('behandelt fehlende Werte als null-Betrag', () => {
    expect(calcFixedCostPer100km(null, null, 2000)).toBe(0)
  })
})

describe('calcFixedCostPerMonth', () => {
  it('verteilt die Netto-Fixkosten auf die Monate des Zeitraums', () => {
    expect(calcFixedCostPerMonth(267, 100, 2)).toBeCloseTo(83.5, 2)
  })

  it('gibt null zurueck ohne Monatszahl', () => {
    expect(calcFixedCostPerMonth(267, 100, null)).toBeNull()
    expect(calcFixedCostPerMonth(267, 100, 0)).toBeNull()
  })
})

describe('monthsInRange', () => {
  const now = new Date(2026, 7, 28) // 28.08.2026

  it('kennt die festen Zeitraeume', () => {
    expect(monthsInRange('THIS_MONTH', '', '', now)).toBe(1)
    expect(monthsInRange('LAST_MONTH', '', '', now)).toBe(1)
    expect(monthsInRange('LAST_3_MONTHS', '', '', now)).toBe(3)
    expect(monthsInRange('LAST_6_MONTHS', '', '', now)).toBe(6)
    expect(monthsInRange('LAST_12_MONTHS', '', '', now)).toBe(12)
  })

  it('zaehlt bei THIS_YEAR die bisher angefangenen Monate', () => {
    expect(monthsInRange('THIS_YEAR', '', '', now)).toBe(8) // Januar bis August
  })

  it('gibt fuer ALL_TIME null zurueck - dort liefert das Backend keine Fixkosten', () => {
    expect(monthsInRange('ALL_TIME', '', '', now)).toBeNull()
  })

  it('rechnet den eigenen Zeitraum aus den Datumsgrenzen', () => {
    expect(monthsInRange('CUSTOM', '2026-01-01', '2026-03-31', now)).toBeCloseTo(2.96, 1)
    expect(monthsInRange('CUSTOM', '2026-06-01', '2026-06-30', now)).toBeCloseTo(0.95, 1)
  })

  it('gibt null zurueck, wenn der eigene Zeitraum unvollstaendig ist', () => {
    expect(monthsInRange('CUSTOM', '', '2026-03-31', now)).toBeNull()
    expect(monthsInRange('CUSTOM', '2026-03-31', '', now)).toBeNull()
  })
})

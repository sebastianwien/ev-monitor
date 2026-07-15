import { describe, it, expect } from 'vitest'
import { shouldRefetchPriceOnToggle } from '../costSuggestion'

describe('shouldRefetchPriceOnToggle', () => {
  it('fetcht keinen Vorschlag ohne Standort - ohne lat/lng gibt es nichts zu suchen', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: false, costLocalTotal: null, costLocalPerKwh: null, costEur: null,
    })).toBe(false)
  })

  it('fetcht in leere Felder mit Standort - der eigentliche Zweck des Vorschlags', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: true, costLocalTotal: null, costLocalPerKwh: null, costEur: null,
    })).toBe(true)
  })

  it('ueberschreibt NIE einen manuell eingegebenen Gesamtpreis (Bug: AC->DC loeschte ihn)', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: true, costLocalTotal: 12.5, costLocalPerKwh: null, costEur: 12.5,
    })).toBe(false)
  })

  it('ueberschreibt NIE einen manuell eingegebenen kWh-Preis', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: true, costLocalTotal: null, costLocalPerKwh: 0.39, costEur: 8.2,
    })).toBe(false)
  })

  it('ueberschreibt NIE einen gespeicherten Preis im Edit-Modus (costEur gesetzt, lokal noch nicht abgeleitet)', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: true, costLocalTotal: null, costLocalPerKwh: null, costEur: 15.0,
    })).toBe(false)
  })

  it('behandelt 0 als vorhandenen Wert (Gratis-Ladung) - nicht ueberschreiben', () => {
    expect(shouldRefetchPriceOnToggle({
      hasLocation: true, costLocalTotal: 0, costLocalPerKwh: null, costEur: 0,
    })).toBe(false)
  })
})

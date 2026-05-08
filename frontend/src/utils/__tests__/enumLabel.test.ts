import { describe, it, expect } from 'vitest'
import { enumToLabel, carDisplayName } from '../enumLabel'

describe('enumToLabel', () => {
  it('konvertiert Unterstriche zu Leerzeichen und setzt Großbuchstaben', () => {
    expect(enumToLabel('POLESTAR_2')).toBe('Polestar 2')
    expect(enumToLabel('VOLKSWAGEN')).toBe('Volkswagen')
    expect(enumToLabel('ID_3')).toBe('Id 3')
  })

  it('gibt leeren String bei null/undefined zurück', () => {
    expect(enumToLabel(null)).toBe('')
    expect(enumToLabel(undefined)).toBe('')
  })
})

describe('carDisplayName', () => {
  it('zeigt "Polestar 2" statt "Polestar Polestar 2"', () => {
    expect(carDisplayName('POLESTAR', 'POLESTAR_2')).toBe('Polestar 2')
  })

  it('zeigt "Volkswagen Id 3" wenn Modell nicht mit Marke beginnt', () => {
    expect(carDisplayName('VOLKSWAGEN', 'ID_3')).toBe('Volkswagen Id 3')
  })

  it('behandelt null/undefined Brand oder Model', () => {
    expect(carDisplayName(null, 'POLESTAR_2')).toBe('Polestar 2')
    expect(carDisplayName('VOLKSWAGEN', null)).toBe('Volkswagen')
    expect(carDisplayName(null, null)).toBe('')
  })

  it('deupliziert case-insensitiv', () => {
    expect(carDisplayName('Tesla', 'TESLA_MODEL_3')).toBe('Tesla Model 3')
  })
})

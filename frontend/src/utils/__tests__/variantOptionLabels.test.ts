import { describe, it, expect } from 'vitest'
import { buildOptionLabels } from '../variantOptionLabels'
import type { CapacityOption } from '../../api/carService'

const opt = (o: Partial<CapacityOption>): CapacityOption => ({
  kWh: 75, variantName: null, vehicleSpecificationId: null,
  trimLevel: 'Long Range RWD', availableFrom: null, availableTo: null, ...o,
})

describe('buildOptionLabels', () => {
  it('zeigt den Zeitraum oben und die Variante ohne Modell-Praefix darunter', () => {
    const labels = buildOptionLabels([
      opt({ availableFrom: '2024-02-01', availableTo: '2025-01-31', variantName: 'Model Y LR RWD (2024-2025)' }),
      opt({ availableFrom: '2025-02-01', availableTo: '2025-10-31', variantName: 'Model Y LR RWD Juniper (2025)' }),
      opt({ availableFrom: '2025-11-01', variantName: 'Model Y Premium RWD Juniper' }),
    ], 'Model Y')
    expect(labels).toEqual([
      { primary: '02/24–01/25', secondary: 'LR RWD (2024-2025)' },
      { primary: '02/25–10/25', secondary: 'LR RWD Juniper (2025)' },
      { primary: 'seit 11/25', secondary: 'Premium RWD Juniper' },
    ])
  })

  it('laesst die zweite Zeile weg, wenn es keinen Variantennamen gibt', () => {
    const labels = buildOptionLabels([opt({ availableFrom: '2025-10-01' })], 'Model Y')
    expect(labels).toEqual([{ primary: 'seit 10/25', secondary: null }])
  })

  it('entfernt den Modell-Praefix nur bei exaktem Treffer', () => {
    const labels = buildOptionLabels([
      opt({ availableFrom: '2022-01-01', variantName: 'ID.3 Pro S' }),
    ], 'ID.4')
    expect(labels).toEqual([{ primary: 'seit 01/22', secondary: 'ID.3 Pro S' }])
  })

  it('faellt auf Variante bzw. Kapazitaet zurueck, wenn kein Zeitraum gepflegt ist', () => {
    const labels = buildOptionLabels([
      opt({ variantName: 'Model X Plaid' }),
      opt({ variantName: null, kWh: 95 }),
    ], 'Model X')
    expect(labels).toEqual([
      { primary: 'Plaid', secondary: null },
      { primary: '95 kWh', secondary: null },
    ])
  })

  it('haengt die Variante an, wenn zwei Optionen denselben Zeitraum tragen', () => {
    const labels = buildOptionLabels([
      opt({ availableFrom: '2022-01-01', availableTo: '2025-01-31', variantName: 'Model Y LR RWD' }),
      opt({ availableFrom: '2022-01-01', availableTo: '2025-01-31', variantName: 'Model Y LR RWD Juniper' }),
    ], 'Model Y')
    expect(labels.map(l => l.primary)).toEqual(['01/22–01/25', '01/22–01/25'])
    expect(labels.map(l => l.secondary)).toEqual(['LR RWD', 'LR RWD Juniper'])
  })

  it('respektiert das uebergebene since-Label', () => {
    const labels = buildOptionLabels([opt({ availableFrom: '2025-10-01' })], 'Model Y', 'since')
    expect(labels[0].primary).toBe('since 10/25')
  })
})

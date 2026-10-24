import { describe, it, expect } from 'vitest'
import { carDisplayName } from '../enumLabel'
import { carLabel } from '../../composables/useCarLabel'

describe('carDisplayName', () => {
  it('gibt den fertigen Namen aus dem Backend zurueck, ohne eigene Formatierung', () => {
    expect(carDisplayName({ displayName: 'VW ID.5' })).toBe('VW ID.5')
  })

  it('ist leer ohne Auto', () => {
    expect(carDisplayName(null)).toBe('')
    expect(carDisplayName(undefined)).toBe('')
  })
})

describe('carLabel', () => {
  it('haengt Ausstattung und Kennzeichen an', () => {
    expect(carLabel({ displayName: 'VW ID.5', trim: 'GTX', licensePlate: 'B-EV 1' })).toBe('VW ID.5 GTX · B-EV 1')
    expect(carLabel({ displayName: 'Polestar 2', trim: null, licensePlate: '' })).toBe('Polestar 2')
  })
})

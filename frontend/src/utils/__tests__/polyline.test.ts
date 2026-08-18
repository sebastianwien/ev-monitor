import { describe, it, expect } from 'vitest'
import { decodePolyline } from '../polyline'

describe('decodePolyline', () => {
  it('dekodiert das Beispiel aus der Google-Spezifikation', () => {
    const points = decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')
    expect(points).toEqual([
      [38.5, -120.2],
      [40.7, -120.95],
      [43.252, -126.453],
    ])
  })

  it('liefert bei leerer Eingabe eine leere Liste', () => {
    expect(decodePolyline('')).toEqual([])
    expect(decodePolyline(null)).toEqual([])
  })

  it('bricht bei kaputter Eingabe nicht ab', () => {
    expect(() => decodePolyline('###')).not.toThrow()
  })
})

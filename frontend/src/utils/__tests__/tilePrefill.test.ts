import { describe, it, expect } from 'vitest'
import { readTilePrefill } from '../tilePrefill'

describe('readTilePrefill', () => {
  it('reads the commute and the holiday trip the partner tile sends', () => {
    expect(readTilePrefill({ pendeln: '40', urlaub: '500' })).toEqual({ commuteKmPerDay: 40, longTripKm: 500 })
  })

  it('takes either one alone', () => {
    expect(readTilePrefill({ pendeln: '20' })).toEqual({ commuteKmPerDay: 20 })
    expect(readTilePrefill({ urlaub: '1200' })).toEqual({ longTripKm: 1200 })
  })

  it('ignores anything that is not a positive whole number within reason', () => {
    expect(readTilePrefill({ pendeln: 'abc', urlaub: '-5' })).toEqual({})
    expect(readTilePrefill({ pendeln: '0', urlaub: '99999' })).toEqual({})
    expect(readTilePrefill({ pendeln: '40.5' })).toEqual({})
    expect(readTilePrefill({ pendeln: ['40', '60'] })).toEqual({})
    expect(readTilePrefill({})).toEqual({})
  })
})

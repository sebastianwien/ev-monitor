import { describe, expect, it } from 'vitest'
import { betterMarks, compareSetKey } from '../compareModels'

describe('betterMarks', () => {
  it('marks the lower value when lower is better', () => {
    expect(betterMarks([20.0, 16.4], true)).toEqual([false, true])
  })

  it('marks the higher value when higher is better', () => {
    expect(betterMarks([350, 420], false)).toEqual([false, true])
  })

  it('marks nothing on a tie', () => {
    expect(betterMarks([17.6, 17.6], true)).toEqual([false, false])
  })

  it('marks every model that shares the best value when one is behind', () => {
    expect(betterMarks([17.6, 19.1, 17.6], true)).toEqual([true, false, true])
  })

  it('marks nothing with fewer than two values', () => {
    expect(betterMarks([null, 16.4], true)).toEqual([false, false])
  })

  it('ignores a missing value among three', () => {
    expect(betterMarks([18, null, 16], true)).toEqual([false, false, true])
  })
})

describe('compareSetKey', () => {
  it('sorts the model keys, so A,B and B,A count as one set', () => {
    expect(compareSetKey(['Volkswagen/ID.3', 'Tesla/Model_3'])).toBe('Tesla/Model_3,Volkswagen/ID.3')
    expect(compareSetKey(['Tesla/Model_3', 'Volkswagen/ID.3'])).toBe('Tesla/Model_3,Volkswagen/ID.3')
  })
})

import { describe, it, expect } from 'vitest'
import { isIncomeCategory, toSignedAmount, toInputAmount } from '../fixedCostAmount'

describe('isIncomeCategory', () => {
  it('marks income and compensation as income categories', () => {
    expect(isIncomeCategory('INCOME')).toBe(true)
    expect(isIncomeCategory('COMPENSATION')).toBe(true)
  })

  it('marks cost categories as non-income', () => {
    expect(isIncomeCategory('INSURANCE')).toBe(false)
    expect(isIncomeCategory('OTHER')).toBe(false)
  })
})

describe('toSignedAmount', () => {
  it('stores income categories as negative', () => {
    expect(toSignedAmount(350, 'COMPENSATION')).toBe(-350)
  })

  it('never flips an already negative income amount back to positive', () => {
    expect(toSignedAmount(-350, 'INCOME')).toBe(-350)
  })

  it('keeps cost categories untouched', () => {
    expect(toSignedAmount(89.9, 'INSURANCE')).toBe(89.9)
  })

  it('keeps a negative cost amount (refund) as entered', () => {
    expect(toSignedAmount(-40, 'INSURANCE')).toBe(-40)
  })

  it('treats a missing amount as zero', () => {
    expect(toSignedAmount(null, 'INCOME')).toBe(0)
    expect(toSignedAmount(Number.NaN, 'TAX')).toBe(0)
  })
})

describe('toInputAmount', () => {
  it('shows income categories as a positive input value', () => {
    expect(toInputAmount(-350, 'COMPENSATION')).toBe(350)
  })

  it('keeps cost categories as stored', () => {
    expect(toInputAmount(89.9, 'INSURANCE')).toBe(89.9)
    expect(toInputAmount(-40, 'INSURANCE')).toBe(-40)
  })

  it('round-trips through the form without drifting', () => {
    const stored = toSignedAmount(toInputAmount(-350, 'INCOME'), 'INCOME')
    expect(stored).toBe(-350)
  })
})

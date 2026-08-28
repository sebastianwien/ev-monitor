import { describe, it, expect } from 'vitest'
import { isIncomeCategory, toInputAmount } from '../fixedCostAmount'

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

describe('toInputAmount', () => {
  it('shows income categories as a positive input value', () => {
    expect(toInputAmount(-350, 'COMPENSATION')).toBe(350)
  })

  it('keeps cost categories as stored', () => {
    expect(toInputAmount(89.9, 'INSURANCE')).toBe(89.9)
    expect(toInputAmount(-40, 'INSURANCE')).toBe(-40)
  })

  it('leaves a zero amount alone', () => {
    expect(toInputAmount(0, 'INCOME')).toBe(0)
  })
})

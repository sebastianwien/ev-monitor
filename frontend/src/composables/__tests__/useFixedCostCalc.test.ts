import { describe, it, expect, vi, afterEach } from 'vitest'
import { useFixedCostCalc, type FixedCostMode } from '../useFixedCostCalc'
import type { FixedCost, FixedCostCategory, FixedCostRecurrence } from '../../api/fixedCostService'

vi.mock('../../api/fixedCostService', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fixedCostService: { list: vi.fn() },
}))

let seq = 0

function fc(partial: {
  amount: number
  category?: FixedCostCategory
  recurrence: FixedCostRecurrence
  date?: string | null
  startDate?: string | null
  endDate?: string | null
}): FixedCost {
  return {
    id: `id-${seq++}`,
    carId: 'car-1',
    description: 'test',
    category: partial.category ?? 'OTHER',
    amount: partial.amount,
    recurrence: partial.recurrence,
    date: partial.date ?? null,
    startDate: partial.startDate ?? null,
    endDate: partial.endDate ?? null,
    createdAt: '2024-01-01T00:00:00Z',
  }
}

function setup(entries: FixedCost[], mode: FixedCostMode = 'pro_rata') {
  const calc = useFixedCostCalc(() => 'car-1', () => mode)
  calc.items.value = entries
  return calc
}

afterEach(() => {
  vi.useRealTimers()
})

describe('calcForMonth - due_month', () => {
  it('counts a one-time entry only in its own month', () => {
    const { calcForMonth } = setup([fc({ amount: 120, recurrence: 'ONE_TIME', date: '2024-06-15' })], 'due_month')

    expect(calcForMonth(2024, 6)).toBe(120)
    expect(calcForMonth(2024, 7)).toBe(0)
  })

  it('counts a monthly entry in every month from its start date', () => {
    const { calcForMonth } = setup([fc({ amount: 89, recurrence: 'MONTHLY', startDate: '2024-04-01' })], 'due_month')

    expect(calcForMonth(2024, 3)).toBe(0)
    expect(calcForMonth(2024, 5)).toBe(89)
  })

  it('stops counting a monthly entry after its end date', () => {
    const { calcForMonth } = setup(
      [fc({ amount: 89, recurrence: 'MONTHLY', startDate: '2024-04-01', endDate: '2024-05-31' })],
      'due_month',
    )

    expect(calcForMonth(2024, 5)).toBe(89)
    expect(calcForMonth(2024, 6)).toBe(0)
  })

  it('counts a yearly entry only in its anniversary month', () => {
    const { calcForMonth } = setup([fc({ amount: 240, recurrence: 'YEARLY', startDate: '2024-03-10' })], 'due_month')

    expect(calcForMonth(2025, 3)).toBe(240)
    expect(calcForMonth(2025, 4)).toBe(0)
  })
})

describe('calcForMonth - pro_rata', () => {
  it('spreads a yearly entry evenly across the months', () => {
    const { calcForMonth } = setup([fc({ amount: 240, recurrence: 'YEARLY', startDate: '2024-03-10' })])

    expect(calcForMonth(2024, 5)).toBe(20)
  })

  it('spreads a one-time entry across its purchase year only', () => {
    const { calcForMonth } = setup([fc({ amount: 120, recurrence: 'ONE_TIME', date: '2024-06-15' })])

    expect(calcForMonth(2024, 1)).toBe(10)
    expect(calcForMonth(2025, 1)).toBe(0)
  })
})

describe('income entries (negative amounts)', () => {
  it('subtracts a one-time income from the same month', () => {
    const { calcForMonth } = setup(
      [
        fc({ amount: 89, category: 'INSURANCE', recurrence: 'ONE_TIME', date: '2024-06-01' }),
        fc({ amount: -350, category: 'COMPENSATION', recurrence: 'ONE_TIME', date: '2024-06-20' }),
      ],
      'due_month',
    )

    expect(calcForMonth(2024, 6)).toBe(-261)
  })

  it('spreads a yearly income across the months in pro_rata mode', () => {
    const { calcForMonth } = setup([
      fc({ amount: 89, category: 'INSURANCE', recurrence: 'MONTHLY', startDate: '2024-01-01' }),
      fc({ amount: -240, category: 'COMPENSATION', recurrence: 'YEARLY', startDate: '2024-01-01' }),
    ])

    expect(calcForMonth(2024, 6)).toBe(69)
  })

  it('keeps income in its own category series so the chart can stack it downwards', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2024, 5, 15))
    const { last12MonthsPerCategory } = setup(
      [
        fc({ amount: 89, category: 'INSURANCE', recurrence: 'MONTHLY', startDate: '2024-01-01' }),
        fc({ amount: -25, category: 'INCOME', recurrence: 'MONTHLY', startDate: '2024-01-01' }),
      ],
      'due_month',
    )

    const income = last12MonthsPerCategory.value.find(s => s.category === 'INCOME')
    const insurance = last12MonthsPerCategory.value.find(s => s.category === 'INSURANCE')

    expect(income?.data.at(-1)).toBe(-25)
    expect(insurance?.data.at(-1)).toBe(89)
  })
})

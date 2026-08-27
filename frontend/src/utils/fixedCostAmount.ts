import type { FixedCostCategory } from '../api/fixedCostService'

/**
 * Categories that represent money coming in (THG-Quote, Untermiete, ...).
 * They are stored as negative fixed costs so every consumer can simply sum up
 * `amount` and gets the net cost.
 */
export const INCOME_CATEGORIES: FixedCostCategory[] = ['INCOME', 'COMPENSATION']

export function isIncomeCategory(category: FixedCostCategory): boolean {
  return INCOME_CATEGORIES.includes(category)
}

/** Form input -> stored value. Income categories are always stored negative. */
export function toSignedAmount(input: number | null | undefined, category: FixedCostCategory): number {
  if (input == null || Number.isNaN(input)) return 0
  return isIncomeCategory(category) ? -Math.abs(input) : input
}

/** Stored value -> form input. Income categories are entered as a positive number. */
export function toInputAmount(amount: number, category: FixedCostCategory): number {
  return isIncomeCategory(category) ? Math.abs(amount) : amount
}

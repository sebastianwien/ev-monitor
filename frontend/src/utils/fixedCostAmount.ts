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

/**
 * Stored value -> form input. Income categories are entered as a positive number; das Vorzeichen
 * setzt das Backend (FixedCost.normalizeAmount), damit die Invariante nur an einer Stelle lebt.
 */
export function toInputAmount(amount: number, category: FixedCostCategory): number {
  return isIncomeCategory(category) ? Math.abs(amount) : amount
}

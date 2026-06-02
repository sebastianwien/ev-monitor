import { ref, computed } from 'vue'
import { fixedCostService, type FixedCost } from '../api/fixedCostService'

export type FixedCostMode = 'pro_rata' | 'due_month'

export function useFixedCostCalc(carId: () => string | null, mode: () => FixedCostMode = () => 'pro_rata') {
  const items = ref<FixedCost[]>([])

  async function load() {
    const id = carId()
    if (!id) return
    items.value = await fixedCostService.list(id)
  }

  function calcForMonth(year: number, month: number): number {
    const from = new Date(year, month - 1, 1)
    const to = new Date(year, month, 0)
    const raw = items.value.reduce((sum, fc) => sum + amountForMonth(fc, from, to), 0)
    return Math.round(raw * 100) / 100
  }

  function amountForMonth(fc: FixedCost, from: Date, to: Date): number {
    if (fc.recurrence === 'ONE_TIME') {
      if (!fc.date) return 0
      const d = new Date(fc.date)
      if (mode() === 'pro_rata') {
        // spread evenly over the 12 months of the purchase year
        if (from.getFullYear() !== d.getFullYear()) return 0
        return Math.round((fc.amount / 12) * 100) / 100
      }
      return d >= from && d <= to ? fc.amount : 0
    }

    const startDate = fc.startDate ? new Date(fc.startDate) : null
    const endDate = fc.endDate ? new Date(fc.endDate) : null
    if (!startDate) return 0
    if (startDate > to) return 0
    if (endDate && endDate < from) return 0

    if (mode() === 'pro_rata') {
      const intervalMonths = fc.recurrence === 'MONTHLY' ? 1 : fc.recurrence === 'QUARTERLY' ? 3 : 12
      return Math.round((fc.amount / intervalMonths) * 100) / 100
    }

    if (fc.recurrence === 'MONTHLY') return fc.amount

    // due_month: full amount only in the month it's due
    const intervalMonths = fc.recurrence === 'QUARTERLY' ? 3 : 12
    return hasRecurrenceHit(startDate, from, to, intervalMonths) ? fc.amount : 0
  }

  function hasRecurrenceHit(anchor: Date, from: Date, to: Date, intervalMonths: number): boolean {
    let cursor = new Date(anchor)
    while (cursor <= to) {
      if (cursor >= from) return true
      cursor = addMonths(cursor, intervalMonths)
    }
    return false
  }

  function addMonths(d: Date, months: number): Date {
    const result = new Date(d)
    result.setMonth(result.getMonth() + months)
    return result
  }

  const last12Months = computed(() => {
    const now = new Date()
    const result = []
    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
      const year = d.getFullYear()
      const month = d.getMonth() + 1
      result.push({ year, month, amount: calcForMonth(year, month) })
    }
    return result
  })

  const last12MonthsPerCategory = computed(() => {
    const now = new Date()
    const months: { year: number; month: number }[] = []
    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
      months.push({ year: d.getFullYear(), month: d.getMonth() + 1 })
    }
    const categories = [...new Set(items.value.map(fc => fc.category))]
    return categories.map(category => ({
      category,
      data: months.map(({ year, month }) => {
        const from = new Date(year, month - 1, 1)
        const to = new Date(year, month, 0)
        const raw = items.value
          .filter(fc => fc.category === category)
          .reduce((sum, fc) => sum + amountForMonth(fc, from, to), 0)
        return Math.round(raw * 100) / 100
      }),
    }))
  })

  return { items, load, calcForMonth, last12Months, last12MonthsPerCategory }
}

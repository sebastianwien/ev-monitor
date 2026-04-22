import { describe, it, expect } from 'vitest'
import { groupCapacitiesByTrim } from '../useCarForm'
import type { CapacityOption } from '../../api/carService'

const cap = (trimLevel: string | null, availableFrom: string | null = null, availableTo: string | null = null): CapacityOption => ({
  kWh: 75, variantName: null, vehicleSpecificationId: `id-${Math.random()}`,
  trimLevel, availableFrom, availableTo,
})

describe('groupCapacitiesByTrim', () => {
  it('gibt leeres Array zurück für leere Liste', () => {
    expect(groupCapacitiesByTrim([])).toEqual([])
  })

  it('gruppiert nach trimLevel', () => {
    const caps = [cap('Long Range AWD', '2021-01-01'), cap('Long Range AWD', '2023-01-01'), cap('Performance', '2021-01-01')]
    const groups = groupCapacitiesByTrim(caps)
    expect(groups).toHaveLength(2)
    expect(groups[0].trimLevel).toBe('Long Range AWD')
    expect(groups[0].options).toHaveLength(2)
    expect(groups[1].trimLevel).toBe('Performance')
  })

  it('bewahrt Einfügereihenfolge der Trim-Level', () => {
    const caps = [cap('Performance'), cap('Standard Range'), cap('Long Range AWD')]
    const groups = groupCapacitiesByTrim(caps)
    expect(groups.map(g => g.trimLevel)).toEqual(['Performance', 'Standard Range', 'Long Range AWD'])
  })

  it('sortiert Optionen innerhalb einer Gruppe nach availableFrom aufsteigend', () => {
    const caps = [
      cap('Long Range AWD', '2023-01-01'),
      cap('Long Range AWD', '2021-01-01'),
      cap('Long Range AWD', '2025-01-01'),
    ]
    const [group] = groupCapacitiesByTrim(caps)
    expect(group.options[0].availableFrom).toBe('2021-01-01')
    expect(group.options[1].availableFrom).toBe('2023-01-01')
    expect(group.options[2].availableFrom).toBe('2025-01-01')
  })

  it('sortiert null-Datum-Einträge ans Ende', () => {
    const caps = [cap('Long Range AWD', null), cap('Long Range AWD', '2021-01-01')]
    const [group] = groupCapacitiesByTrim(caps)
    expect(group.options[0].availableFrom).toBe('2021-01-01')
    expect(group.options[1].availableFrom).toBeNull()
  })

  it('schließt Einträge ohne trimLevel aus', () => {
    const caps = [cap('Long Range AWD'), cap(null), cap('Performance')]
    const groups = groupCapacitiesByTrim(caps)
    expect(groups).toHaveLength(2)
    expect(groups.every(g => g.trimLevel !== null)).toBe(true)
  })
})

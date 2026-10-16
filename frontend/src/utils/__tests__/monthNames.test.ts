import { describe, it, expect } from 'vitest'
import { monthNames } from '../monthNames'

describe('monthNames', () => {
  it('liefert zwölf Monate, Januar zuerst', () => {
    const names = monthNames('de')
    expect(names).toHaveLength(12)
    expect(names[0]).toBe('Januar')
    expect(names[11]).toBe('Dezember')
  })
})

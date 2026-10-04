import { describe, it, expect } from 'vitest'
import { isLocationInaccurate, formatDistance } from '../placeDistance'

describe('isLocationInaccurate', () => {
  it('erst ab mehr als 500 m Ungenauigkeit', () => {
    expect(isLocationInaccurate(500)).toBe(false)
    expect(isLocationInaccurate(1200)).toBe(true)
    expect(isLocationInaccurate(null)).toBe(false)
  })
})

describe('formatDistance', () => {
  it('Meter unter 1 km, sonst km mit einer Stelle', () => {
    expect(formatDistance(480, 'de')).toBe('480 m')
    expect(formatDistance(4120, 'de')).toBe('4,1 km')
    expect(formatDistance(2500, 'en')).toBe('2.5 km')
    expect(formatDistance(1000, 'de')).toBe('1 km')
  })
})

import { describe, it, expect } from 'vitest'
import { pricelessDeepLink } from '../pricelessDeepLink'

const cars = [{ id: 'car-a' }, { id: 'car-b' }]

describe('pricelessDeepLink', () => {
  it('ignores a normal visit', () => {
    expect(pricelessDeepLink({}, cars)).toBeNull()
    expect(pricelessDeepLink({ car: 'car-b' }, cars)).toBeNull()
  })

  it('opens for the car from the link', () => {
    expect(pricelessDeepLink({ nachtragen: 'preis', car: 'car-b' }, cars)).toEqual({ carId: 'car-b' })
  })

  it('keeps the current car when the link names a car the user does not have', () => {
    expect(pricelessDeepLink({ nachtragen: 'preis', car: 'someone-else' }, cars)).toEqual({ carId: null })
    expect(pricelessDeepLink({ nachtragen: 'preis' }, cars)).toEqual({ carId: null })
  })

  it('ignores repeated query values', () => {
    expect(pricelessDeepLink({ nachtragen: ['preis', 'preis'], car: ['car-b'] }, cars)).toBeNull()
  })
})

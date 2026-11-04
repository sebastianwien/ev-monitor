import { describe, it, expect } from 'vitest'
import { ciTopic } from '../ciTopic'

describe('ciTopic', () => {
  it('ordnet Kacheln und Feed-Zeilen Geld oder Verbrauch zu', () => {
    expect(ciTopic('cost')).toBe('money')
    expect(ciTopic('cpo_price')).toBe('money')
    expect(ciTopic('home_savings')).toBe('money')
    expect(ciTopic('wltp')).toBe('energy')
    expect(ciTopic('standby_change')).toBe('energy')
  })

  it('ohne Thema undefined, damit kein Attribut entsteht', () => {
    expect(ciTopic('trips')).toBeUndefined()
    expect(ciTopic('data_quality')).toBeUndefined()
  })
})

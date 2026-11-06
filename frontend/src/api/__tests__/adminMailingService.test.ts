import { describe, expect, it } from 'vitest'
import { goalRate } from '../adminMailingService'

describe('goalRate', () => {
  it('ist der Anteil der Besuche mit erreichtem Goal in Prozent', () => {
    expect(goalRate(3, 12)).toBe(25)
    expect(goalRate(1, 3)).toBe(33)
  })

  it('gibt null ohne Besuche, statt durch null zu teilen', () => {
    expect(goalRate(0, 0)).toBeNull()
    expect(goalRate(2, 0)).toBeNull()
  })

  it('deckelt bei 100, weil Goal-Besucher und Besuche getrennt gezählt werden', () => {
    expect(goalRate(5, 4)).toBe(100)
  })
})

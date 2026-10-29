import { describe, it, expect } from 'vitest'
import { lockAxis, cardRelease, rubberY } from '../useCardSwipe'

const W = 400

describe('lockAxis', () => {
  it('unter 8 px ist es noch ein Tipp', () => {
    expect(lockAxis(3, 4)).toBeNull()
    expect(lockAxis(-7, 0)).toBeNull()
  })
  it('danach entscheidet die stärkere Richtung', () => {
    expect(lockAxis(12, 3)).toBe('x')
    expect(lockAxis(-12, 3)).toBe('x')
    expect(lockAxis(2, 12)).toBe('y')
    expect(lockAxis(2, -12)).toBe('y')
  })
})

describe('cardRelease seitlich', () => {
  it('ab gut einem Drittel der Breite fliegt die Karte raus, in beide Richtungen', () => {
    expect(cardRelease({ axis: 'x', dx: 0.4 * W, dy: 0, v: 0, width: W })).toBe('dismiss')
    expect(cardRelease({ axis: 'x', dx: -0.4 * W, dy: 0, v: 0, width: W })).toBe('dismiss')
  })
  it('langsam und kurz federt sie zurück', () => {
    expect(cardRelease({ axis: 'x', dx: 0.2 * W, dy: 0, v: 0.1, width: W })).toBe('back')
  })
  it('ein schneller Wisch reicht auch kurz, aber nicht unter 24 px', () => {
    expect(cardRelease({ axis: 'x', dx: 30, dy: 0, v: 0.8, width: W })).toBe('dismiss')
    expect(cardRelease({ axis: 'x', dx: -30, dy: 0, v: -0.8, width: W })).toBe('dismiss')
    expect(cardRelease({ axis: 'x', dx: 10, dy: 0, v: 2, width: W })).toBe('back')
  })
  it('wer weit zieht und zurückschnippt, behält die Karte', () => {
    expect(cardRelease({ axis: 'x', dx: 0.5 * W, dy: 0, v: -0.8, width: W })).toBe('back')
  })
})

describe('cardRelease nach unten', () => {
  it('ab 96 px oder mit schnellem Zug klappt der Stapel ein', () => {
    expect(cardRelease({ axis: 'y', dx: 0, dy: 120, v: 0, width: W })).toBe('collapse')
    expect(cardRelease({ axis: 'y', dx: 0, dy: 40, v: 0.8, width: W })).toBe('collapse')
  })
  it('kurz, nach oben oder zurückgeschnippt federt er zurück', () => {
    expect(cardRelease({ axis: 'y', dx: 0, dy: 40, v: 0.1, width: W })).toBe('back')
    expect(cardRelease({ axis: 'y', dx: 0, dy: -60, v: -1, width: W })).toBe('back')
    expect(cardRelease({ axis: 'y', dx: 0, dy: 120, v: -0.8, width: W })).toBe('back')
  })
})

describe('rubberY', () => {
  it('nach unten folgt die Karte dem Finger, nach oben gibt sie nur federnd nach', () => {
    expect(rubberY(50)).toBe(50)
    expect(rubberY(-50)).toBe(-10)
  })
})

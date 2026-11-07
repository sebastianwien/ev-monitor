import { describe, it, expect } from 'vitest'
import { snapAfterDrag, nextOnTap, snapHeights } from '../snapSheet'

const H = { peek: 180, half: 400, full: 760 }

describe('snapAfterDrag', () => {
  it('ohne Schwung: die nächstgelegene Höhe', () => {
    expect(snapAfterDrag(H, 250, 0)).toBe('peek')
    expect(snapAfterDrag(H, 330, 0)).toBe('half')
    expect(snapAfterDrag(H, 620, 0)).toBe('full')
  })

  it('schneller Wisch nach oben: die nächste größere Höhe, auch kurz nach dem Start', () => {
    expect(snapAfterDrag(H, 200, 0.8)).toBe('half')
    expect(snapAfterDrag(H, 420, 0.8)).toBe('full')
  })

  it('schneller Wisch nach unten: die nächste kleinere Höhe', () => {
    expect(snapAfterDrag(H, 740, -0.8)).toBe('half')
    expect(snapAfterDrag(H, 380, -0.8)).toBe('peek')
  })

  it('an den Enden bleibt es dort', () => {
    expect(snapAfterDrag(H, 780, 1)).toBe('full')
    expect(snapAfterDrag(H, 150, -1)).toBe('peek')
  })
})

describe('nextOnTap', () => {
  it('klein, halb, ganz, dann wieder klein', () => {
    expect(nextOnTap('peek')).toBe('half')
    expect(nextOnTap('half')).toBe('full')
    expect(nextOnTap('full')).toBe('peek')
  })
})

describe('snapHeights', () => {
  it('halb ist die Hälfte, ganz lässt oben einen Streifen Karte frei', () => {
    expect(snapHeights(800, 180)).toEqual({ peek: 180, half: 400, full: 744 })
  })

  it('kleiner Bildschirm: halb nie unter klein, ganz nie unter halb', () => {
    const h = snapHeights(300, 180)
    expect(h.half).toBeGreaterThanOrEqual(h.peek)
    expect(h.full).toBeGreaterThanOrEqual(h.half)
  })
})

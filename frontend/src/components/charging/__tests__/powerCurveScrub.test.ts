import { describe, it, expect } from 'vitest'
import { nearestIndexByX, formatDuration } from '../powerCurveScrub'

describe('nearestIndexByX', () => {
  const xs = [0, 100, 200, 300, 600]

  it('liefert -1 ohne Punkte', () => {
    expect(nearestIndexByX([], 42)).toBe(-1)
  })

  it('trifft den exakten Punkt', () => {
    expect(nearestIndexByX(xs, 200)).toBe(2)
  })

  it('rundet zum naeheren Nachbarn', () => {
    expect(nearestIndexByX(xs, 140)).toBe(1)
    expect(nearestIndexByX(xs, 160)).toBe(2)
  })

  it('nimmt bei exakter Mitte den linken Punkt', () => {
    expect(nearestIndexByX(xs, 150)).toBe(1)
  })

  it('klemmt ausserhalb der Kurve auf den Randpunkt', () => {
    expect(nearestIndexByX(xs, -50)).toBe(0)
    expect(nearestIndexByX(xs, 9999)).toBe(4)
  })

  it('kommt mit einem einzelnen Punkt klar', () => {
    expect(nearestIndexByX([300], 0)).toBe(0)
  })

  it('findet auch bei ungleichmaessigen Abstaenden den naechsten Punkt', () => {
    // Tesla streamt on-change: lange Pause, dann dichte Folge
    const uneven = [0, 10, 20, 30, 580, 600]
    expect(nearestIndexByX(uneven, 400)).toBe(4)
    expect(nearestIndexByX(uneven, 100)).toBe(3)
  })
})

describe('formatDuration', () => {
  it('zeigt Minuten unter einer Stunde', () => {
    expect(formatDuration(45 * 60_000)).toBe('45 Min')
  })

  it('zeigt volle Stunden ohne Minutenanteil', () => {
    expect(formatDuration(120 * 60_000)).toBe('2 h')
  })

  it('zeigt Stunden mit Minutenanteil', () => {
    expect(formatDuration(80 * 60_000)).toBe('1h 20')
  })
})

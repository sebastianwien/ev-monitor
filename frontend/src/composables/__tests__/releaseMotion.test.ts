import { describe, it, expect } from 'vitest'
import { releaseMs, settleMs, RELEASE_MIN_MS, RELEASE_MAX_MS, RELEASE_START_SLOPE } from '../releaseMotion'

describe('releaseMs', () => {
  it('startet mit dem Tempo des Fingers: Dauer = Anfangssteigung × Restweg / Tempo', () => {
    // 300 px Restweg mit 3 px/ms: die Kurve beginnt mit 3 px/ms, wenn sie 300 ms dauert
    expect(releaseMs(300, 3)).toBe(RELEASE_START_SLOPE * 300 / 3)
  })
  it('ein schneller Wisch läuft kürzer, aber nie unter dem Minimum', () => {
    expect(releaseMs(300, 1.5)).toBeGreaterThan(releaseMs(300, 3))
    expect(releaseMs(40, 5)).toBe(RELEASE_MIN_MS)
  })
  it('ohne Tempo (Finger stand still, Knopf) die längste Dauer', () => {
    expect(releaseMs(300, 0)).toBe(RELEASE_MAX_MS)
    expect(releaseMs(2000, 0.1)).toBe(RELEASE_MAX_MS)
  })
  it('Richtung zählt nicht, nur der Betrag', () => {
    expect(releaseMs(-300, -3)).toBe(releaseMs(300, 3))
  })
  it('ohne Restweg das Minimum', () => {
    expect(releaseMs(0, 2)).toBe(RELEASE_MIN_MS)
  })
})

describe('settleMs', () => {
  it('kurzer Weg zurück kurz, langer länger, beides begrenzt', () => {
    expect(settleMs(10)).toBe(RELEASE_MIN_MS)
    expect(settleMs(200)).toBeGreaterThan(settleMs(60))
    expect(settleMs(5000)).toBe(RELEASE_MAX_MS)
  })
})

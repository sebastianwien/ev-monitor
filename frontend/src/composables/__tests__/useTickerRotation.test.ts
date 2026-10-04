import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick, effectScope } from 'vue'
import { dwellMs, useTickerRotation } from '../useTickerRotation'

describe('dwellMs', () => {
  it('gibt kurzen Texten mindestens 4 Sekunden', () => {
    expect(dwellMs(5)).toBe(4000)
  })
  it('wächst mit der Textlänge und deckelt bei 9 Sekunden', () => {
    expect(dwellMs(80)).toBeGreaterThan(dwellMs(40))
    expect(dwellMs(500)).toBe(9000)
  })
  it('rechnet die Durchlaufzeit überlanger Texte obendrauf', () => {
    expect(dwellMs(40, 2000)).toBe(dwellMs(40) + 2000)
  })
})

describe('useTickerRotation', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  function setup(count = 3) {
    const n = ref(count)
    const paused = ref(false)
    const scope = effectScope()
    const r = scope.run(() => useTickerRotation({ count: n, paused, dwell: () => 1000 }))!
    return { n, paused, r, scope }
  }

  it('schaltet nach der Anzeigedauer weiter und läuft im Kreis', () => {
    const { r } = setup(2)
    expect(r.index.value).toBe(0)
    vi.advanceTimersByTime(1000)
    expect(r.index.value).toBe(1)
    vi.advanceTimersByTime(1000)
    expect(r.index.value).toBe(0)
  })

  it('steht still, solange pausiert ist, und plant keinen Timer', async () => {
    const { r, paused } = setup()
    paused.value = true
    await nextTick()
    vi.advanceTimersByTime(10_000)
    expect(r.index.value).toBe(0)
    expect(vi.getTimerCount()).toBe(0)
    paused.value = false
    await nextTick()
    vi.advanceTimersByTime(1000)
    expect(r.index.value).toBe(1)
  })

  it('next() springt sofort weiter und startet die Anzeigedauer neu', () => {
    const { r } = setup()
    vi.advanceTimersByTime(900)
    r.next()
    expect(r.index.value).toBe(1)
    vi.advanceTimersByTime(900)
    expect(r.index.value).toBe(1)
    vi.advanceTimersByTime(100)
    expect(r.index.value).toBe(2)
  })

  it('rotiert bei nur einem Eintrag nicht', () => {
    const { r } = setup(1)
    vi.advanceTimersByTime(5000)
    expect(r.index.value).toBe(0)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('räumt den Timer beim Beenden des Scopes ab', () => {
    const { scope } = setup()
    scope.stop()
    expect(vi.getTimerCount()).toBe(0)
  })
})

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useTickerPingPong, roundTripMs, PING_PONG_HOLD_MS } from '../useTickerPingPong'

/* 120 px Überlänge bei 40 px/s: 3 s Fahrt je Richtung. */
const OVER = 120
const SCROLL = 3000

describe('useTickerPingPong', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  function setup(paused = false) {
    const overflowPx = ref(OVER)
    const pausedRef = ref(paused)
    const scope = effectScope()
    const api = scope.run(() => useTickerPingPong({ overflowPx, paused: pausedRef }))!
    api.reset()
    return { ...api, pausedRef, scope }
  }

  it('hält am Anfang, läuft nach links, hält, läuft zurück und wieder hin', () => {
    const { atEnd } = setup()
    expect(atEnd.value).toBe(false)

    vi.advanceTimersByTime(PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(true)

    vi.advanceTimersByTime(SCROLL + PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(false)

    vi.advanceTimersByTime(SCROLL + PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(true)
  })

  it('bleibt bei Pause stehen und läuft danach weiter', async () => {
    const { atEnd, pausedRef } = setup()
    vi.advanceTimersByTime(PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(true)

    pausedRef.value = true
    await nextTick()
    vi.advanceTimersByTime(60_000)
    expect(atEnd.value).toBe(true)

    pausedRef.value = false
    await nextTick()
    vi.advanceTimersByTime(PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(false)
  })

  it('bewegt nichts ohne Überlänge', () => {
    const { atEnd, reset } = effectScope().run(() => useTickerPingPong({ overflowPx: ref(0), paused: ref(false) }))!
    reset()
    vi.advanceTimersByTime(60_000)
    expect(atEnd.value).toBe(false)
  })

  it('reset springt an den Anfang zurück', () => {
    const { atEnd, reset } = setup()
    vi.advanceTimersByTime(PING_PONG_HOLD_MS)
    expect(atEnd.value).toBe(true)

    reset()
    expect(atEnd.value).toBe(false)
  })

  it('einmal hin und zurück dauert zwei Fahrten plus zwei Haltezeiten', () => {
    expect(roundTripMs(OVER)).toBe(2 * (SCROLL + PING_PONG_HOLD_MS))
    expect(roundTripMs(0)).toBe(0)
  })
})

// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp } from 'vue'
import { useDelayedCall } from '../useDelayedCall'

const mount = (fn: () => void, ms: number) => {
  let delayed!: ReturnType<typeof useDelayedCall>
  const app = createApp({ setup() { delayed = useDelayedCall(fn, ms); return () => null } })
  app.mount(document.createElement('div'))
  return { delayed, app }
}

describe('useDelayedCall', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('ruft erst nach der Wartezeit auf', () => {
    const fn = vi.fn()
    const { delayed } = mount(fn, 350)
    delayed.schedule()
    vi.advanceTimersByTime(349)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(fn).toHaveBeenCalledTimes(1)
  })

  it('ein erneuter Tap startet die Wartezeit neu statt doppelt zu feuern', () => {
    const fn = vi.fn()
    const { delayed } = mount(fn, 350)
    delayed.schedule()
    vi.advanceTimersByTime(200)
    delayed.schedule()
    vi.advanceTimersByTime(200)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(150)
    expect(fn).toHaveBeenCalledTimes(1)
  })

  it('cancel und Unmount lassen nichts mehr feuern', () => {
    const fn = vi.fn()
    const { delayed, app } = mount(fn, 350)
    delayed.schedule()
    delayed.cancel()
    vi.advanceTimersByTime(400)
    expect(fn).not.toHaveBeenCalled()
    delayed.schedule()
    app.unmount()
    vi.advanceTimersByTime(400)
    expect(fn).not.toHaveBeenCalled()
  })
})

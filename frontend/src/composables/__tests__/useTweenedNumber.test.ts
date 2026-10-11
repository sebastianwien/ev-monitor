// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useTweenedNumber } from '../useTweenedNumber'

function mockReducedMotion(reduce: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduce && query.includes('prefers-reduced-motion'),
    media: query, addEventListener: vi.fn(), removeEventListener: vi.fn(),
  })) as never
}

describe('useTweenedNumber', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame', 'performance', 'setTimeout', 'Date'] })
  })
  afterEach(() => vi.useRealTimers())

  it('starts at the source value without animating', () => {
    mockReducedMotion(false)
    const scope = effectScope()
    const shown = scope.run(() => useTweenedNumber(ref(280)))!

    expect(shown.value).toBe(280)
    scope.stop()
  })

  it('counts towards a new value and lands exactly on it', async () => {
    mockReducedMotion(false)
    const source = ref(280)
    const scope = effectScope()
    const shown = scope.run(() => useTweenedNumber(source, { duration: 600 }))!

    source.value = 340
    await nextTick()
    vi.advanceTimersByTime(300)
    expect(shown.value).toBeGreaterThan(280)
    expect(shown.value).toBeLessThan(340)

    vi.advanceTimersByTime(400)
    expect(shown.value).toBe(340)
    scope.stop()
  })

  it('jumps straight to the new value when the reader asks for reduced motion', async () => {
    mockReducedMotion(true)
    const source = ref(280)
    const scope = effectScope()
    const shown = scope.run(() => useTweenedNumber(source))!

    source.value = 340
    await nextTick()

    expect(shown.value).toBe(340)
    scope.stop()
  })
})

import { onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue'

/**
 * A number that counts towards its source instead of jumping: ease-out over `duration` ms,
 * rounded to whole units. Starts at the source value without animating, and jumps straight
 * to a new value when the reader asks for reduced motion.
 */
export function useTweenedNumber(source: MaybeRefOrGetter<number>, opts: { duration?: number } = {}): Ref<number> {
  const duration = opts.duration ?? 700
  const shown = ref(Math.round(toValue(source)))
  let frame: number | null = null

  function stop() {
    if (frame != null) cancelAnimationFrame(frame)
    frame = null
  }

  watch(() => toValue(source), target => {
    stop()
    const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (reduce || duration <= 0) {
      shown.value = Math.round(target)
      return
    }
    const from = shown.value
    const start = performance.now()
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      shown.value = Math.round(from + (target - from) * eased)
      frame = progress < 1 ? requestAnimationFrame(step) : null
    }
    frame = requestAnimationFrame(step)
  })

  onScopeDispose(stop)
  return shown
}

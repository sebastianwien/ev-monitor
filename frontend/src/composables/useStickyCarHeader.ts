import { ref, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * Tracks when a `position: sticky` element is "stuck" against the app bar
 * (top-16 = 64px by default), so consumers can switch to a compact layout.
 *
 * Uses a scroll listener with requestAnimationFrame throttling because
 * IntersectionObserver-on-sentinel had edge cases around the 64px threshold
 * and display:none on desktop.
 *
 * Usage:
 *   const stickyCarBar = ref<HTMLElement | null>(null)
 *   const { isCarHeaderSticky } = useStickyCarHeader(stickyCarBar)
 *   <div ref="stickyCarBar" :class="isCarHeaderSticky ? 'compact' : ''">
 */
export function useStickyCarHeader(
  target: Ref<HTMLElement | null>,
  stuckThresholdPx = 64,
): { isCarHeaderSticky: Ref<boolean> } {
  const isCarHeaderSticky = ref(false)

  let rafId = 0
  const measure = () => {
    rafId = 0
    if (!target.value) return
    isCarHeaderSticky.value = target.value.getBoundingClientRect().top <= stuckThresholdPx
  }
  const schedule = () => {
    if (rafId !== 0) return
    rafId = requestAnimationFrame(measure)
  }

  onMounted(() => {
    window.addEventListener('scroll', schedule, { passive: true })
    window.addEventListener('resize', schedule, { passive: true })
    // Initial check after layout
    schedule()
  })
  onUnmounted(() => {
    window.removeEventListener('scroll', schedule)
    window.removeEventListener('resize', schedule)
    if (rafId !== 0) cancelAnimationFrame(rafId)
  })

  return { isCarHeaderSticky }
}

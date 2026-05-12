import { onMounted, onUnmounted, watch, type Ref } from 'vue'

const CSS_VAR = '--bulk-bar-offset'
const MOBILE_QUERY = '(max-width: 767px)'

/**
 * Writes a CSS variable on the document root so the global FloatingActionButton
 * can lift itself when a view shows a sticky bottom bar (filter on Dashboard,
 * bulk-toggle on Logs).
 *
 * The bar element is observed via ResizeObserver — no need to hard-code the
 * bar's height. If `barEl` is null or the viewport is not mobile, the offset
 * is cleared to `0px`.
 *
 * Usage:
 *   const barEl = ref<HTMLElement | null>(null)
 *   useBulkBarOffset(barEl, computed(() => visible.value))
 *   <div ref="barEl" v-if="visible" class="md:hidden fixed bottom-0 ...">
 */
export function useBulkBarOffset(
  barEl: Ref<HTMLElement | null>,
  visible: Ref<boolean>,
): void {
  let resizeObserver: ResizeObserver | null = null

  function clear() {
    document.documentElement.style.setProperty(CSS_VAR, '0px')
  }
  function sync() {
    const onMobile = window.matchMedia(MOBILE_QUERY).matches
    if (!onMobile || !visible.value || !barEl.value) {
      clear()
      return
    }
    // Match the bar's actual rendered height. fallback if measurement is 0.
    const h = barEl.value.getBoundingClientRect().height
    document.documentElement.style.setProperty(CSS_VAR, h > 0 ? `${Math.round(h)}px` : '0px')
  }

  watch(visible, sync, { immediate: true })
  watch(barEl, (el) => {
    resizeObserver?.disconnect()
    resizeObserver = null
    if (el && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(sync)
      resizeObserver.observe(el)
    }
    sync()
  })

  onMounted(() => {
    window.addEventListener('resize', sync, { passive: true })
    sync()
  })
  onUnmounted(() => {
    window.removeEventListener('resize', sync)
    resizeObserver?.disconnect()
    clear()
  })
}

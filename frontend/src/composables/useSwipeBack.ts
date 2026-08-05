import { onMounted, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'

/** Breite der linken Randzone, in der ein Zurueck-Wisch beginnen muss. */
export const EDGE_ZONE_PX = 30

/** Mindest-Horizontaldistanz nach rechts, ab der zurueck navigiert wird. */
export const SWIPE_THRESHOLD_PX = 80

/** True, wenn der Touch im linken Randbereich begann (schuetzt mittige Carousels). */
export function isInEdgeZone(startX: number): boolean {
  return startX >= 0 && startX <= EDGE_ZONE_PX
}

/** True, wenn weit genug nach rechts und ueberwiegend horizontal gewischt wurde. */
export function shouldTriggerBack(deltaX: number, deltaY: number): boolean {
  return deltaX >= SWIPE_THRESHOLD_PX && deltaX > Math.abs(deltaY)
}

/** True, wenn das Element selbst ein tatsaechlich horizontal scrollbarer Container ist. */
export function isHorizontalScroller(el: Element): boolean {
  const ox = getComputedStyle(el).overflowX
  return (ox === 'auto' || ox === 'scroll') && el.scrollWidth > el.clientWidth
}

/**
 * True, wenn der Touch in einem horizontalen Scroller (z.B. Edge-to-Edge Card-Row)
 * begann. Dann darf die Zurueck-Geste nicht greifen, sonst scrollt der Container UND
 * navigiert gleichzeitig zurueck. Laeuft vom Target die Vorfahren hoch.
 */
export function startsInsideHorizontalScroller(target: EventTarget | null): boolean {
  let el = target instanceof Element ? target : null
  while (el) {
    if (isHorizontalScroller(el)) return true
    el = el.parentElement
  }
  return false
}

/**
 * True, wenn der Touch in einem Element mit {@code data-no-swipe} begann. Fuer
 * Bedienelemente, die horizontale Gesten selbst auswerten (z.B. das Abtasten der
 * Ladekurve) - dort wuerde ein Tab-Wechsel oder Zurueck-Wisch die Bedienung kapern.
 */
export function startsInsideNoSwipeZone(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest('[data-no-swipe]') !== null
}

/**
 * iOS-typischer Edge-Swipe-Back: vom linken Rand nach rechts wischen => zurueck.
 *
 * Nur in der nativen App aktiv. Im mobilen Browser bringt die Plattform die
 * Zurueck-Geste selbst mit - dort wuerde ein eigener Handler doppelt navigieren,
 * daher bleibt das Composable auf Web/PWA komplett inaktiv (keine Listener, kein Leak).
 *
 * @param back Navigations-Aktion (Default: ein Schritt in der History zurueck).
 */
export function useSwipeBack(back: () => void = () => window.history.back()) {
  let startX = 0
  let startY = 0
  let tracking = false

  function onStart(e: TouchEvent) {
    if (
      e.touches.length !== 1 ||
      !isInEdgeZone(e.touches[0].clientX) ||
      startsInsideHorizontalScroller(e.target) ||
      startsInsideNoSwipeZone(e.target)
    ) {
      tracking = false
      return
    }
    tracking = true
    startX = e.touches[0].clientX
    startY = e.touches[0].clientY
  }

  function onEnd(e: TouchEvent) {
    if (!tracking) return
    tracking = false
    const t = e.changedTouches[0]
    if (!t) return
    if (shouldTriggerBack(t.clientX - startX, t.clientY - startY)) back()
  }

  onMounted(() => {
    if (!Capacitor.isNativePlatform()) return
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchend', onEnd, { passive: true })
    window.addEventListener('touchcancel', onEnd, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('touchstart', onStart)
    window.removeEventListener('touchend', onEnd)
    window.removeEventListener('touchcancel', onEnd)
  })
}

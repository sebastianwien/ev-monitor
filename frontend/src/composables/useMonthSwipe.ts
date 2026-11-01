import { ref, watch, onUnmounted, type Ref } from 'vue'
import { SWIPE_THRESHOLD_PX, isInEdgeZone, startsInsideHorizontalScroller } from './useSwipeBack'

/** Ab hier gilt eine Geste als waagerecht oder senkrecht. */
const DECIDE_PX = 8
/** Ohne Ziel in Wischrichtung gibt die Karte nur zäh nach. */
const RUBBER = 0.25

export type SwipeSide = 'prev' | 'next'

/** Wohin führt ein Wisch beim Loslassen? Nach rechts zum vorigen Ziel, nach links zum nächsten. */
export function monthSwipeTarget(dx: number, hasPrev: boolean, hasNext: boolean, threshold = SWIPE_THRESHOLD_PX): SwipeSide | null {
  if (dx >= threshold && hasPrev) return 'prev'
  if (dx <= -threshold && hasNext) return 'next'
  return null
}

/**
 * Waagerechter Wisch auf einer Karte: sie geht mit dem Finger mit, daneben öffnet sich ein Streifen
 * mit dem Ziel. Beim Loslassen über der Schwelle `onSelect`, sonst federt sie zurück.
 */
export function useMonthSwipe(
  target: Ref<HTMLElement | null>,
  has: () => { prev: boolean; next: boolean },
  onSelect: (side: SwipeSide) => void,
) {
  const dragX = ref(0)
  const dragging = ref(false)
  let startX = 0
  let startY = 0
  let active = false
  let decided = false
  let horizontal = false

  function onStart(e: TouchEvent) {
    if (e.touches.length !== 1) { active = false; return }
    const x = e.touches[0].clientX
    if (isInEdgeZone(x) || startsInsideHorizontalScroller(e.target)) { active = false; return }
    startX = x
    startY = e.touches[0].clientY
    active = true
    decided = false
    horizontal = false
  }

  function onMove(e: TouchEvent) {
    if (!active) return
    const dx = e.touches[0].clientX - startX
    const dy = e.touches[0].clientY - startY
    if (!decided) {
      if (Math.abs(dx) < DECIDE_PX && Math.abs(dy) < DECIDE_PX) return
      decided = true
      horizontal = Math.abs(dx) > Math.abs(dy)
      if (horizontal) dragging.value = true
    }
    if (!horizontal) return
    const h = has()
    dragX.value = (dx > 0 ? h.prev : h.next) ? dx : dx * RUBBER
    if (e.cancelable) e.preventDefault()
  }

  function onEnd() {
    if (!active) return
    active = false
    if (!horizontal) return
    const h = has()
    const side = monthSwipeTarget(dragX.value, h.prev, h.next)
    dragging.value = false
    dragX.value = 0
    if (side) onSelect(side)
  }

  function attach(el: HTMLElement) {
    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchmove', onMove, { passive: false })
    el.addEventListener('touchend', onEnd, { passive: true })
    el.addEventListener('touchcancel', onEnd, { passive: true })
  }
  function detach(el: HTMLElement) {
    el.removeEventListener('touchstart', onStart)
    el.removeEventListener('touchmove', onMove)
    el.removeEventListener('touchend', onEnd)
    el.removeEventListener('touchcancel', onEnd)
  }
  watch(target, (el, old) => {
    if (old) detach(old)
    if (el) attach(el)
  }, { immediate: true })
  onUnmounted(() => { if (target.value) detach(target.value) })

  return { dragX, dragging, threshold: SWIPE_THRESHOLD_PX }
}

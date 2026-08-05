import { ref, watch, onUnmounted, type Ref } from 'vue'
import {
  EDGE_ZONE_PX,
  SWIPE_THRESHOLD_PX,
  isInEdgeZone,
  startsInsideHorizontalScroller,
  startsInsideNoSwipeZone,
} from './useSwipeBack'

/**
 * Reine Entscheidung: bei welchem Horizontal-Delta und aktuellem Tab wird
 * gewechselt? Immer nur zum direkten Nachbarn. Testbar ohne DOM.
 */
export function pagerNavTarget(
  dx: number,
  activeIndex: number,
  tabs: readonly string[],
  threshold = SWIPE_THRESHOLD_PX,
): string | null {
  if (dx <= -threshold) return tabs[activeIndex + 1] ?? null  // nach links wischen -> rechter Nachbar
  if (dx >= threshold) return tabs[activeIndex - 1] ?? null    // nach rechts wischen -> linker Nachbar
  return null
}

/** Achtsamkeits-Schwelle, ab der eine Geste als horizontaler Drag gilt. */
const DECIDE_PX = 8
/** Wischen ins Leere (kein Nachbar) darf nur zaeh nachgeben. */
const RUBBER = 0.25

/**
 * Instagram-artiger Tab-Pager: horizontaler Drag auf dem Body zieht ihn mit dem
 * Finger, beim Loslassen ueber der Schwelle wird zum Nachbar-Tab navigiert
 * (die Slide-Transition uebernimmt dann den Einflug), sonst federt es zurueck.
 *
 * Nur nativ sinnvoll (Touch). Guards: startet die Geste in der linken Edge-Zone
 * (dem globalen Zurueck-Wisch vorbehalten) oder in einem horizontalen Scroller
 * (Auto-Card-Reihe), greift der Pager nicht.
 */
export function useTabPager(
  target: Ref<HTMLElement | null>,
  activeIndex: Ref<number>,
  tabs: Ref<readonly string[]>,
  navigate: (to: string) => void,
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
    if (isInEdgeZone(x) || startsInsideHorizontalScroller(e.target) || startsInsideNoSwipeZone(e.target)) { active = false; return }
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
    const hasNeighbor = pagerNavTarget(dx < 0 ? -Infinity : Infinity, activeIndex.value, tabs.value) !== null
    dragX.value = hasNeighbor ? dx : dx * RUBBER
    if (e.cancelable) e.preventDefault() // vertikales Scrollen waehrend des H-Drags unterdruecken
  }

  function onEnd() {
    if (!active) return
    active = false
    if (!horizontal) return
    const to = pagerNavTarget(dragX.value, activeIndex.value, tabs.value)
    dragging.value = false
    dragX.value = 0
    if (to) navigate(to)
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

  return { dragX, dragging }
}

// Re-Export fuer Konsumenten, die nur die Schwelle brauchen.
export { EDGE_ZONE_PX, SWIPE_THRESHOLD_PX }

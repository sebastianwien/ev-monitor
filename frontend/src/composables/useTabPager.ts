import { ref, watch, onUnmounted, type Ref } from 'vue'
import {
  EDGE_ZONE_PX,
  SWIPE_THRESHOLD_PX,
  isInEdgeZone,
  startsInsideHorizontalScroller,
  startsInsideNoSwipeZone,
} from './useSwipeBack'
import { releaseMs as momentumMs, settleMs } from './releaseMotion'

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
/** Länger als ein Routenwechsel je dauern sollte: danach federt der Track sicher zurück. */
const HOLD_MAX_MS = 1500
/** Wischen ins Leere (kein Nachbar) darf nur zaeh nachgeben. */
const RUBBER = 0.25

/**
 * Instagram-artiger Tab-Pager: horizontaler Drag auf dem Body zieht ihn mit dem
 * Finger, beim Loslassen ueber der Schwelle wird zum Nachbar-Tab navigiert
 * (die Slide-Transition uebernimmt dann den Einflug), sonst federt es zurueck.
 *
 * Loslassen wie ein nativer Pager: über der Schwelle bleibt der Track an der Fingerposition stehen
 * (`held`), bis die Route gewechselt hat, und läuft dann mit dem Tempo des Fingers weiter
 * (`releaseMs`); kein Zurückfedern, während der Router noch prüft.
 *
 * Nur nativ sinnvoll (Touch). Guards: startet die Geste in der linken Edge-Zone
 * (dem globalen Zurueck-Wisch vorbehalten) oder in einem horizontalen Scroller
 * (Auto-Card-Reihe), greift der Pager nicht.
 */
export function useTabPager(
  target: Ref<HTMLElement | null>,
  activeIndex: Ref<number>,
  tabs: Ref<readonly string[]>,
  navigate: (to: string) => unknown,
) {
  const dragX = ref(0)
  const dragging = ref(false)
  /** Losgelassen über der Schwelle, die Route wechselt gerade: der Track steht an der Fingerposition. */
  const held = ref(false)
  /** Dauer der Bewegung nach dem Loslassen; null = Standard (z. B. Tab-Klick). */
  const releaseMs = ref<number | null>(null)
  let last = { x: 0, t: 0 }
  let prev = { x: 0, t: 0 }
  let releaseTimer: ReturnType<typeof setTimeout> | undefined

  function settle(ms: number) {
    releaseMs.value = ms
    clearTimeout(releaseTimer)
    releaseTimer = setTimeout(() => { releaseMs.value = null }, ms + 40)
  }

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
    last = prev = { x, t: e.timeStamp }
    active = true
    // Fällt der berührte Knoten während des Wischs aus dem DOM (Monatswechsel), blubbert touchend
    // nicht mehr bis zum Pager hoch und die Seite bliebe verschoben stehen. Am Knoten selbst kommt es an.
    const node = e.target
    if (node instanceof EventTarget && node !== target.value) {
      node.addEventListener('touchend', onEnd, { passive: true, once: true })
      node.addEventListener('touchcancel', onEnd, { passive: true, once: true })
    }
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
    prev = last
    last = { x: e.touches[0].clientX, t: e.timeStamp }
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
    if (!to) {
      settle(settleMs(dragX.value))
      dragX.value = 0
      return
    }
    // Finger stand vor dem Loslassen still: kein Schwung mehr
    const dt = last.t - prev.t
    const v = dt > 0 && dt < 80 ? (last.x - prev.x) / dt : 0
    const width = target.value?.getBoundingClientRect().width || window.innerWidth
    // Nur Schwung in Wischrichtung trägt weiter
    const speed = Math.sign(v) === Math.sign(dragX.value) ? v : 0
    pendingMs = momentumMs(width - Math.abs(dragX.value), speed)
    held.value = true
    // Scheitert die Navigation (router.push liefert dann einen Fehler statt undefined), federt der Track
    // zurück. Gelingt sie, wartet er auf den neuen Tab-Index, der erst im nächsten Durchlauf nachzieht.
    clearTimeout(holdTimer)
    holdTimer = setTimeout(springBack, HOLD_MAX_MS)
    void Promise.resolve(navigate(to)).then(failure => { if (failure) springBack() }, springBack)
  }
  let pendingMs = 0
  let holdTimer: ReturnType<typeof setTimeout> | undefined

  function springBack() {
    clearTimeout(holdTimer)
    if (!held.value) return
    held.value = false
    settle(settleMs(dragX.value))
    dragX.value = 0
  }

  // Die Route hat gewechselt: der Track läuft von der Fingerposition zum neuen Tab
  watch(activeIndex, () => {
    if (!held.value) return
    clearTimeout(holdTimer)
    held.value = false
    settle(pendingMs)
    dragX.value = 0
  }, { flush: 'sync' })

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

  onUnmounted(() => { clearTimeout(releaseTimer); clearTimeout(holdTimer); if (target.value) detach(target.value) })

  return { dragX, dragging, held, releaseMs }
}

// Re-Export fuer Konsumenten, die nur die Schwelle brauchen.
export { EDGE_ZONE_PX, SWIPE_THRESHOLD_PX }

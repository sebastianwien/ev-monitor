import { computed, ref } from 'vue'
import { cardRelease, rubberY } from './useCardSwipe'

const MOVE_MS = 200

/** Ein Scrollbereich zwischen Finger und Panel steht nicht ganz oben: dann scrollt erst er zurück. */
function scrolledDown(target: Element | null, root: Element): boolean {
  for (let el = target; el && el !== root.parentElement; el = el.parentElement) {
    if (el.scrollTop > 0 && el.scrollHeight > el.clientHeight && /auto|scroll/.test(getComputedStyle(el).overflowY)) return true
  }
  return false
}

/** Karte ([data-no-sheet-drag]) und Flächen mit eigener Geste (touch-action: none, z. B. Regler) behalten den Finger. */
function ownGesture(target: Element | null, root: Element): boolean {
  for (let el = target; el && el !== root.parentElement; el = el.parentElement) {
    if (el instanceof HTMLElement && (el.dataset.noSheetDrag != null || getComputedStyle(el).touchAction === 'none')) return true
  }
  return false
}

/** Ein Textfeld im Sheet hat den Fokus: ein Wisch soll nichts Getipptes wegwerfen. */
const TYPING = 'input:not([type=checkbox]):not([type=radio]):not([type=range]), textarea, select, [contenteditable="true"]'
function typing(root: Element): boolean {
  const a = document.activeElement
  return !!a && root.contains(a) && a.matches(TYPING)
}

/**
 * Ein Sheet von unten wie eine Karte nach unten ziehen: ab der Schwelle des Neuigkeiten-Stapels
 * oder mit einem schnellen Wisch schließt es, sonst federt es zurück. Im Inhalt nur, wenn der
 * Bereich unter dem Finger ganz oben steht und der erste Schritt nach unten geht; nach oben,
 * seitlich und in gescrolltem Inhalt bleibt alles nativ. Touch- statt Pointer-Events: nur ein
 * nicht-passives touchmove hält den nativen Scroll noch auf, Pointer-Events bekämen pointercancel.
 */
export function useSheetDrag(opts: { enabled: () => boolean; close: () => void }) {
  const dy = ref(0)
  const phase = ref<'idle' | 'drag' | 'back' | 'out'>('idle')
  let start: { x: number; y: number } | null = null
  /** null: erster Schritt steht noch aus; danach gehört die Geste dem Sheet oder nicht. */
  let claimed: boolean | null = null
  let last = { y: 0, t: 0 }
  let prev = { y: 0, t: 0 }

  function springBack() {
    phase.value = 'back'
    dy.value = 0
    setTimeout(() => { if (phase.value === 'back') phase.value = 'idle' }, MOVE_MS)
  }

  function onTouchStart(e: TouchEvent) {
    // Ein zweiter Finger mitten im Zug bricht ihn ab, sonst bliebe das Panel verschoben stehen
    if (phase.value === 'drag') springBack()
    start = null
    const root = e.currentTarget as Element
    const target = e.target as Element | null
    if (e.touches.length !== 1 || phase.value === 'out' || !opts.enabled()) return
    if (typing(root) || ownGesture(target, root) || scrolledDown(target, root)) return
    const p = e.touches[0]
    start = { x: p.clientX, y: p.clientY }
    last = prev = { y: p.clientY, t: e.timeStamp }
    claimed = null
  }

  function onTouchMove(e: TouchEvent) {
    if (!start || claimed === false) return
    const p = e.touches[0]
    const dx = p.clientX - start.x
    const d = p.clientY - start.y
    if (claimed === null) {
      if (dx === 0 && d === 0) return
      claimed = d > 0 && Math.abs(d) >= Math.abs(dx)
      if (!claimed) return
      phase.value = 'drag'
    }
    e.preventDefault()
    prev = last
    last = { y: p.clientY, t: e.timeStamp }
    dy.value = rubberY(d)
  }

  function onTouchEnd() {
    const dragged = start != null && claimed === true
    start = null
    claimed = null
    if (!dragged) return
    const dt = last.t - prev.t
    const v = dt > 0 ? (last.y - prev.y) / dt : 0
    if (cardRelease({ axis: 'y', dx: 0, dy: dy.value, v, width: 0 }) === 'collapse') {
      phase.value = 'out'
      opts.close()
      return
    }
    springBack()
  }

  const reducedMotion = () => !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  /** Inline am Panel: folgt dem Finger, federt zurück oder fährt von der Fingerposition aus hinaus. */
  const style = computed<Record<string, string> | undefined>(() => {
    if (phase.value === 'drag') return { transform: `translateY(${dy.value}px)`, transition: 'none' }
    if (phase.value === 'back') return { transform: 'translateY(0)', transition: `transform ${MOVE_MS}ms ease-out` }
    if (phase.value === 'out') return { transform: 'translateY(100%)', transition: reducedMotion() ? 'none' : `transform ${MOVE_MS}ms ease-in` }
    return undefined
  })

  return { style, onTouchStart, onTouchMove, onTouchEnd }
}

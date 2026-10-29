import { computed, ref, onUnmounted } from 'vue'

/** Ab so vielen px Bewegung steht die Achse fest; darunter ist es ein Tipp. */
export const AXIS_LOCK_PX = 8
/** Seitlich: ab diesem Anteil der Kartenbreite fliegt die Karte raus. */
export const DISMISS_SHARE = 0.35
/** Nach unten: ab so vielen px klappt der Stapel ein. */
export const COLLAPSE_PX = 96
/** Ein schneller Wisch (px/ms) reicht auch unter der Schwelle, wenn er mindestens FLING_MIN_PX weit ging. */
export const FLING_PX_PER_MS = 0.5
export const FLING_MIN_PX = 24
const FLY_MS = 200

export type Axis = 'x' | 'y'
export type CardRelease = 'dismiss' | 'collapse' | 'back'
/** Stand beim Loslassen: Achse, Weg und Tempo entlang der Achse (px/ms, Vorzeichen wie der Weg). */
export interface CardDrag { axis: Axis; dx: number; dy: number; v: number; width: number }

export function lockAxis(dx: number, dy: number): Axis | null {
  if (Math.max(Math.abs(dx), Math.abs(dy)) < AXIS_LOCK_PX) return null
  return Math.abs(dx) > Math.abs(dy) ? 'x' : 'y'
}

/** Seitlich raus heißt erledigt, nach unten heißt einklappen, alles andere federt zurück. */
export function cardRelease({ axis, dx, dy, v, width }: CardDrag): CardRelease {
  const d = axis === 'x' ? dx : dy
  if (axis === 'y' && d <= 0) return 'back'
  const fling = Math.abs(v) >= FLING_PX_PER_MS
  if (fling && Math.sign(v) !== Math.sign(d)) return 'back' // zurückgeschnippt
  const passed = axis === 'x' ? Math.abs(d) >= width * DISMISS_SHARE : d >= COLLAPSE_PX
  if (passed || (fling && Math.abs(d) >= FLING_MIN_PX)) return axis === 'x' ? 'dismiss' : 'collapse'
  return 'back'
}

/** Nach unten folgt die Karte dem Finger, nach oben gibt sie nur federnd nach. */
export function rubberY(dy: number): number {
  return dy < 0 ? dy * 0.2 : dy
}

const reducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/**
 * Karte mit dem Finger wie eine native Karte: seitlich wischen fliegt sie raus (erledigt), nach unten ziehen
 * klappt ein. Die Achse steht nach 8 px fest, vorher ist es ein Tipp und Knöpfe in der Karte bleiben klickbar.
 * Pointer Events, damit Finger, Stift und Maus gleich gehen; die Fläche braucht `touch-action: none`, eine
 * scrollbare Fläche darin `data-scroll` (dort beginnt keine Geste). stackStyle trägt den Zug nach unten,
 * cardStyle den Wisch zur Seite. Schwelle und Kippen rechnen mit der Breite der Karte (`card`), nicht der
 * Fläche: ab md ist die Karte schmaler als der Bildschirm.
 */
export function useCardSwipe(opts: {
  axes: () => Record<Axis, boolean>
  card: () => HTMLElement | null | undefined
  dismiss: () => void
  collapse: () => void
}) {
  const x = ref(0)
  const y = ref(0)
  const dragging = ref(false)
  let width = 1
  let start = { x: 0, y: 0 }
  let last = { pos: 0, t: 0 }
  let v = 0
  let axis: Axis | null = null
  let tracking = false
  let justDragged = false
  let timer: ReturnType<typeof setTimeout> | undefined

  function reset() {
    dragging.value = false
    x.value = 0
    y.value = 0
  }

  /** Karte zur Seite rausfliegen lassen, danach erledigt melden. Auch für das ×. */
  function fly(dir: 1 | -1) {
    if (reducedMotion()) { reset(); opts.dismiss(); return }
    dragging.value = false
    // ganz aus dem Bildschirm, auch wenn die Karte schmaler ist
    x.value = dir * Math.max(width * 1.3, window.innerWidth)
    clearTimeout(timer)
    timer = setTimeout(() => { reset(); opts.dismiss() }, FLY_MS)
  }

  function onPointerdown(e: PointerEvent) {
    justDragged = false
    if (!e.isPrimary || e.button !== 0) return
    if (e.target instanceof Element && e.target.closest('[data-scroll]')) return
    tracking = true
    axis = null
    width = (opts.card() ?? (e.currentTarget as HTMLElement)).getBoundingClientRect().width || 1
    start = { x: e.clientX, y: e.clientY }
    last = { pos: 0, t: e.timeStamp }
    v = 0
  }

  function onPointermove(e: PointerEvent) {
    if (!tracking) return
    const dx = e.clientX - start.x
    const dy = e.clientY - start.y
    if (!axis) {
      axis = lockAxis(dx, dy)
      if (!axis) return
      if (!opts.axes()[axis]) { tracking = false; axis = null; return }
      ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
      dragging.value = true
    }
    const pos = axis === 'x' ? dx : dy
    if (e.timeStamp > last.t) v = (pos - last.pos) / (e.timeStamp - last.t)
    last = { pos, t: e.timeStamp }
    if (axis === 'x') x.value = dx
    else y.value = rubberY(dy)
  }

  function onPointerup(e: PointerEvent) {
    if (!tracking) return
    tracking = false
    if (!axis) return
    justDragged = true
    // Stand der Finger vor dem Loslassen still, zählt kein Schwung mehr
    const speed = e.timeStamp - last.t > 80 ? 0 : v
    const result = cardRelease({ axis, dx: x.value, dy: e.clientY - start.y, v: speed, width })
    if (result === 'dismiss') fly(x.value < 0 ? -1 : 1)
    else if (result === 'collapse') { dragging.value = false; opts.collapse() }
    else reset()
  }

  function onPointercancel() {
    if (!tracking) return
    tracking = false
    reset()
  }

  /** Nach einem Zug löst das Loslassen keinen Klick auf einen Knopf in der Karte aus. */
  function onClickCapture(e: MouseEvent) {
    if (!justDragged) return
    justDragged = false
    e.stopPropagation()
    e.preventDefault()
  }

  const ease = 'cubic-bezier(0.32, 0.72, 0, 1)'
  const transition = computed(() => dragging.value ? 'none' : `transform 220ms ${ease}, opacity 220ms ease`)
  const stackStyle = computed(() => ({ transform: `translateY(${y.value}px)`, transition: transition.value }))
  const cardStyle = computed(() => {
    const share = Math.min(1, Math.abs(x.value) / width)
    return {
      transform: `translateX(${x.value}px) rotate(${(x.value / width) * 6}deg)`,
      transformOrigin: '50% 100%',
      opacity: String(1 - share * 0.6),
      transition: transition.value,
    }
  })

  onUnmounted(() => clearTimeout(timer))

  return {
    stackStyle, cardStyle, fly, reset,
    handlers: { pointerdown: onPointerdown, pointermove: onPointermove, pointerup: onPointerup, pointercancel: onPointercancel },
    onClickCapture,
  }
}

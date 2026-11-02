import { computed, nextTick, ref, onUnmounted } from 'vue'
import { RELEASE_EASE, releaseMs, settleMs } from './releaseMotion'

/** Ab so vielen px Bewegung steht die Achse fest; darunter ist es ein Tipp. */
export const AXIS_LOCK_PX = 8
/** Seitlich: ab diesem Anteil der Kartenbreite fliegt die Karte raus. */
export const DISMISS_SHARE = 0.35
/** Nach unten: ab so vielen px klappt der Stapel ein. */
export const COLLAPSE_PX = 96
/** Ein schneller Wisch (px/ms) reicht auch unter der Schwelle, wenn er mindestens FLING_MIN_PX weit ging. */
export const FLING_PX_PER_MS = 0.5
export const FLING_MIN_PX = 24

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
const nextFrame = (fn: () => void) => typeof requestAnimationFrame === 'function' ? requestAnimationFrame(fn) : setTimeout(fn, 16)

/** Eine Karte, die hinausfliegt, während die nächste schon vorn liegt: Start, Ziel, Dauer. */
export interface LeavingCard { from: number; to: number; ms: number; flying: boolean }

/**
 * Karte mit dem Finger wie eine native Karte: seitlich wischen fliegt sie raus (erledigt), nach unten ziehen
 * klappt ein. Die Achse steht nach 8 px fest, vorher ist es ein Tipp und Knöpfe in der Karte bleiben klickbar.
 * Pointer Events, damit Finger, Stift und Maus gleich gehen; die Fläche braucht `touch-action: none`, eine
 * scrollbare Fläche darin `data-scroll` (dort beginnt keine Geste). stackStyle trägt den Zug nach unten,
 * cardStyle den Wisch zur Seite. Schwelle und Kippen rechnen mit der Breite der Karte (`card`), nicht der
 * Fläche: ab md ist die Karte schmaler als der Bildschirm.
 *
 * Loslassen wie bei nativen Karten: die Karte bleibt voll sichtbar und läuft mit dem Tempo des
 * Fingers weiter (releaseMotion). Mit `overlap` meldet fly sofort erledigt, damit die nächste Karte
 * schon nachrückt; die alte fliegt dann als Bild (`leaving`, `leavingStyle`) darüber hinaus.
 */
export function useCardSwipe(opts: {
  axes: () => Record<Axis, boolean>
  card: () => HTMLElement | null | undefined
  dismiss: () => void
  collapse: () => void
  overlap?: () => boolean
}) {
  const x = ref(0)
  const y = ref(0)
  const dragging = ref(false)
  /** Dauer der Bewegung nach dem Loslassen (Zurückfedern, Hinausfliegen, Einklappen). */
  const moveMs = ref(220)
  const leaveMs = ref(260)
  const leaving = ref<LeavingCard | null>(null)
  let width = 1
  let height = 1
  let start = { x: 0, y: 0 }
  let last = { pos: 0, t: 0 }
  let v = 0
  let axis: Axis | null = null
  let tracking = false
  let justDragged = false
  let timer: ReturnType<typeof setTimeout> | undefined

  function reset() {
    moveMs.value = settleMs(Math.max(Math.abs(x.value), Math.abs(y.value)))
    dragging.value = false
    x.value = 0
    y.value = 0
  }

  /** Karte zur Seite rausfliegen lassen und erledigt melden. Auch für das × (ohne Tempo). */
  function fly(dir: 1 | -1, speed = 0) {
    if (reducedMotion()) { reset(); opts.dismiss(); return }
    clearTimeout(timer)
    const w = opts.card()?.getBoundingClientRect().width || width
    // ganz aus dem Bildschirm, auch wenn die Karte schmaler ist
    const to = dir * Math.max(w * 1.3, window.innerWidth)
    const ms = releaseMs(to - x.value, speed)
    if (opts.overlap?.()) {
      // Die nächste Karte rückt sofort nach; die alte fliegt als Bild von der Fingerposition weiter
      leaving.value = { from: x.value, to, ms, flying: false }
      dragging.value = false
      x.value = 0
      y.value = 0
      opts.dismiss()
      nextFrame(() => nextFrame(() => { if (leaving.value) leaving.value = { ...leaving.value, flying: true } }))
      timer = setTimeout(() => { leaving.value = null }, ms)
      return
    }
    moveMs.value = ms
    dragging.value = false
    x.value = to
    timer = setTimeout(() => { reset(); opts.dismiss() }, ms)
  }

  function onPointerdown(e: PointerEvent) {
    justDragged = false
    if (!e.isPrimary || e.button !== 0) return
    if (e.target instanceof Element && e.target.closest('[data-scroll]')) return
    tracking = true
    axis = null
    width = (opts.card() ?? (e.currentTarget as HTMLElement)).getBoundingClientRect().width || 1
    height = (e.currentTarget as HTMLElement).getBoundingClientRect().height || 1
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
    if (result === 'dismiss') fly(x.value < 0 ? -1 : 1, Math.abs(speed))
    else if (result === 'collapse') {
      // Der Stapel fährt mit dem Finger hinaus. Die Dauer muss am Element stehen, bevor es ausblendet:
      // ein ausblendendes Element patcht Vue nicht mehr.
      leaveMs.value = reducedMotion() ? 0 : releaseMs(height - y.value, Math.max(0, speed))
      dragging.value = false
      void nextTick(opts.collapse)
    }
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

  const transition = computed(() => dragging.value ? 'none' : `transform ${moveMs.value}ms ${RELEASE_EASE}`)
  const tilt = (px: number) => `translateX(${px}px) rotate(${(px / width) * 6}deg)`
  const stackStyle = computed(() => ({ transform: `translateY(${y.value}px)`, transition: transition.value }))
  // Kein Ausblenden: die Karte bleibt beim Wischen und Hinausfliegen ganz sichtbar
  const cardStyle = computed(() => ({ transform: tilt(x.value), transformOrigin: '50% 100%', transition: transition.value }))
  const leavingStyle = computed(() => {
    const l = leaving.value
    if (!l) return undefined
    return {
      transform: tilt(l.flying ? l.to : l.from),
      transformOrigin: '50% 100%',
      transition: l.flying ? `transform ${l.ms}ms ${RELEASE_EASE}` : 'none',
    }
  })

  onUnmounted(() => clearTimeout(timer))

  return {
    stackStyle, cardStyle, leaving, leavingStyle, leaveMs, fly, reset,
    handlers: { pointerdown: onPointerdown, pointermove: onPointermove, pointerup: onPointerup, pointercancel: onPointercancel },
    onClickCapture,
  }
}

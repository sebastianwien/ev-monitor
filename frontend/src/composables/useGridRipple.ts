import { onMounted, onUnmounted, type Ref } from 'vue'

// ---------------------------------------------------------------------------
// Tuning-Konstanten (CSS-px, sofern nicht anders vermerkt)
// ---------------------------------------------------------------------------

/** Rasterweite - identisch zum statischen CSS-Gitter `.sl-grid` (background-size: 28px). */
export const GRID_SIZE = 28
/** Wirkradius der subtilen Cursor-Welle. */
const CURSOR_RADIUS = 110
/** Maximale Auslenkung eines Punktes durch den Cursor. */
const CURSOR_MAX_SHIFT = 3.5
/** Maximale Auslenkung eines Punktes durch einen Klick-Ripple. */
const RIPPLE_MAX_SHIFT = 6
/** Ausbreitungsgeschwindigkeit des Ripple-Rings in px/s. */
const RIPPLE_SPEED = 320
/** Bandbreite des Ripple-Rings (Gauss-Sigma in px). */
const RIPPLE_WIDTH = 46
/** Lebensdauer eines Ripples in ms. */
const RIPPLE_LIFE_MS = 1100
/** Wie lange nach der letzten Mausbewegung noch animiert wird. */
const CURSOR_IDLE_MS = 900
/** Obergrenze gleichzeitiger Ripples (aelteste fallen raus). */
const MAX_RIPPLES = 6
/** devicePixelRatio-Deckel - mehr bringt optisch nichts, kostet aber Fillrate. */
const MAX_DPR = 2

/** Brand-Gruen je Theme (hell `#15803d`, dark `#22c55e`). */
const BRAND_LIGHT = { r: 21, g: 128, b: 61 }
const BRAND_DARK = { r: 34, g: 197, b: 94 }
/** Linienfarbe je Theme - identisch zu `.sl-grid`. */
const LINE_LIGHT = 'rgba(17,24,39,0.035)'
const LINE_DARK = 'rgba(255,255,255,0.04)'

// ---------------------------------------------------------------------------
// Reine, testbare Berechnungslogik
// ---------------------------------------------------------------------------

/**
 * Einfluss eines expandierenden Ripple-Rings auf einen Punkt (0..1).
 *
 * Der Ring ist ein schmales Band um den aktuellen `radius`. Punkte direkt auf
 * dem Band werden maximal beeinflusst, mit gaussscher Abnahme zu den Seiten
 * (Bandbreite `width`). `life` (1=frisch .. 0=tot) daempft die Amplitude
 * quadratisch, damit die Welle weich ausklingt statt hart zu verschwinden.
 */
export function rippleInfluence(dist: number, radius: number, width: number, life: number): number {
  if (life <= 0) return 0
  const d = (dist - radius) / width
  const band = Math.exp(-d * d) // gausssches Band um den Ringradius
  const fade = life * life // weiches Ausklingen
  return band * fade
}

/**
 * Naehe-Einfluss des Cursors auf einen Punkt im Abstand `dist` (0..1).
 * Smoothstep statt linear fuer organischere Kanten; 0 ab `radius`.
 */
export function cursorInfluence(dist: number, radius: number): number {
  if (dist >= radius) return 0
  const t = 1 - dist / radius
  return t * t * (3 - 2 * t)
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

interface Ripple {
  x: number
  y: number
  start: number
}

/**
 * Animiertes, Maus-reaktives Hintergrund-Gitter auf einem Canvas (2D).
 *
 * - Klick loest einen expandierenden Ripple-Ring vom Klickpunkt aus.
 * - Punkte nahe dem Cursor werden subtil ausgelenkt und in Brand-Gruen aufgehellt.
 *
 * Das Canvas zeichnet NUR beeinflusste Punkte. Ruhende Punkte uebernimmt das
 * statische CSS-Gitter `.sl-grid` darunter (identische Optik, null Kosten).
 *
 * Guards (alle Pflicht):
 * - Nur bei `(pointer: fine)` aktiv (kein Touch/Mobile - dort nur CSS-Gitter).
 * - `prefers-reduced-motion: reduce` deaktiviert die Animation komplett.
 * - Pausiert bei verborgenem Tab und ausserhalb des Viewports (IntersectionObserver).
 * - rAF-Loop laeuft nur bei aktiver Arbeit (Ripples / kuerzliche Mausbewegung)
 *   und haelt im Idle an; naechstes Event startet ihn neu.
 *
 * @param canvasRef Ref auf das Ziel-`<canvas>` (fixed, viewport-gross, hinter dem Content).
 */
export function useGridRipple(canvasRef: Ref<HTMLCanvasElement | null>) {
  let ctx: CanvasRenderingContext2D | null = null
  const reduceMotionMq = matchMedia('(prefers-reduced-motion: reduce)')
  const finePointerMq = matchMedia('(pointer: fine)')

  let dpr = 1
  let cssW = 0
  let cssH = 0
  let cols = 0
  let rows = 0

  const ripples: Ripple[] = []
  const cursor = { x: -9999, y: -9999, lastMove: -Infinity }

  let rafId = 0
  let running = false
  let visible = true
  let enabled = true

  let lineColor = LINE_LIGHT
  let brand = BRAND_LIGHT

  let resizeObserver: ResizeObserver | null = null
  let intersectionObserver: IntersectionObserver | null = null

  function readTheme() {
    const dark = document.documentElement.classList.contains('dark')
    lineColor = dark ? LINE_DARK : LINE_LIGHT
    brand = dark ? BRAND_DARK : BRAND_LIGHT
  }

  function resize() {
    const canvas = canvasRef.value
    if (!canvas || !ctx) return
    dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR)
    cssW = canvas.clientWidth
    cssH = canvas.clientHeight
    canvas.width = Math.round(cssW * dpr)
    canvas.height = Math.round(cssH * dpr)
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    cols = Math.ceil(cssW / GRID_SIZE) + 1
    rows = Math.ceil(cssH / GRID_SIZE) + 1
  }

  function hasWork(): boolean {
    if (ripples.length > 0) return true
    return performance.now() - cursor.lastMove < CURSOR_IDLE_MS
  }

  function start() {
    if (running || !enabled || !visible || document.hidden) return
    running = true
    rafId = requestAnimationFrame(frame)
  }

  function stop() {
    running = false
    if (rafId) cancelAnimationFrame(rafId)
    rafId = 0
  }

  function clear() {
    if (ctx) ctx.clearRect(0, 0, cssW, cssH)
  }

  function frame(now: number) {
    if (!running) return

    for (let i = ripples.length - 1; i >= 0; i--) {
      if (now - ripples[i].start > RIPPLE_LIFE_MS) ripples.splice(i, 1)
    }

    draw(now)

    if (hasWork()) {
      rafId = requestAnimationFrame(frame)
    } else {
      clear() // Idle: nur das statische CSS-Gitter bleibt sichtbar
      stop()
    }
  }

  function draw(now: number) {
    if (!ctx) return
    ctx.clearRect(0, 0, cssW, cssH)
    const cursorActive = now - cursor.lastMove < CURSOR_IDLE_MS

    for (let gy = 0; gy < rows; gy++) {
      for (let gx = 0; gx < cols; gx++) {
        const baseX = gx * GRID_SIZE
        const baseY = gy * GRID_SIZE

        let dx = 0
        let dy = 0
        let glow = 0

        // Klick-Ripples schieben den Punkt radial nach aussen
        for (let r = 0; r < ripples.length; r++) {
          const rip = ripples[r]
          const life = 1 - (now - rip.start) / RIPPLE_LIFE_MS
          const radius = ((now - rip.start) / 1000) * RIPPLE_SPEED
          const vx = baseX - rip.x
          const vy = baseY - rip.y
          const dist = Math.hypot(vx, vy)
          const infl = rippleInfluence(dist, radius, RIPPLE_WIDTH, life)
          if (infl > 0.001 && dist > 0.0001) {
            const shift = infl * RIPPLE_MAX_SHIFT
            dx += (vx / dist) * shift
            dy += (vy / dist) * shift
            if (infl > glow) glow = infl
          }
        }

        // Cursor-Welle zieht Punkte leicht zum Cursor und hellt sie auf
        if (cursorActive) {
          const vx = cursor.x - baseX
          const vy = cursor.y - baseY
          const dist = Math.hypot(vx, vy)
          const infl = cursorInfluence(dist, CURSOR_RADIUS)
          if (infl > 0.001 && dist > 0.0001) {
            const shift = infl * CURSOR_MAX_SHIFT
            dx += (vx / dist) * shift
            dy += (vy / dist) * shift
            const g = infl * 0.55
            if (g > glow) glow = g
          }
        }

        const x = baseX + dx
        const y = baseY + dy
        const moved = dx * dx + dy * dy > 0.02

        if (glow > 0.02) {
          const a = Math.min(0.5, glow * 0.55)
          const radiusPt = 1 + glow * 1.4
          ctx.beginPath()
          ctx.fillStyle = `rgba(${brand.r},${brand.g},${brand.b},${a})`
          ctx.arc(x, y, radiusPt, 0, Math.PI * 2)
          ctx.fill()
        } else if (moved) {
          ctx.beginPath()
          ctx.fillStyle = lineColor
          ctx.arc(x, y, 1, 0, Math.PI * 2)
          ctx.fill()
        }
        // Ruhende Punkte zeichnen wir nicht - das CSS-Gitter liefert sie kostenlos.
      }
    }
  }

  // ---- Events -------------------------------------------------------------

  function onPointerMove(e: PointerEvent) {
    cursor.x = e.clientX
    cursor.y = e.clientY
    cursor.lastMove = performance.now()
    start()
  }

  function onPointerDown(e: PointerEvent) {
    if (ripples.length >= MAX_RIPPLES) ripples.shift()
    ripples.push({ x: e.clientX, y: e.clientY, start: performance.now() })
    start()
  }

  function onVisibility() {
    if (document.hidden) stop()
    else start()
  }

  function evalEnabled() {
    enabled = finePointerMq.matches && !reduceMotionMq.matches
    if (!enabled) {
      stop()
      clear()
    }
  }

  function onMqChange() {
    evalEnabled()
    start()
  }

  // ---- Lifecycle ----------------------------------------------------------

  onMounted(() => {
    const canvas = canvasRef.value
    if (!canvas) return
    ctx = canvas.getContext('2d', { alpha: true })
    if (!ctx) return

    readTheme()
    evalEnabled()
    resize()

    resizeObserver = new ResizeObserver(() => {
      resize()
      if (running) draw(performance.now())
    })
    resizeObserver.observe(canvas)

    intersectionObserver = new IntersectionObserver((entries) => {
      visible = entries[0].isIntersecting
      if (visible) start()
      else stop()
    })
    intersectionObserver.observe(canvas)

    window.addEventListener('pointermove', onPointerMove, { passive: true })
    window.addEventListener('pointerdown', onPointerDown, { passive: true })
    document.addEventListener('visibilitychange', onVisibility)
    reduceMotionMq.addEventListener('change', onMqChange)
    finePointerMq.addEventListener('change', onMqChange)
  })

  onUnmounted(() => {
    stop()
    resizeObserver?.disconnect()
    intersectionObserver?.disconnect()
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerdown', onPointerDown)
    document.removeEventListener('visibilitychange', onVisibility)
    reduceMotionMq.removeEventListener('change', onMqChange)
    finePointerMq.removeEventListener('change', onMqChange)
    clear()
    ctx = null
  })

  /** Theme-Wechsel (Light/Dark) von aussen melden, damit Farben aktualisiert werden. */
  function refreshTheme() {
    readTheme()
    if (running) draw(performance.now())
  }

  return { refreshTheme }
}

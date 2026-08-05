// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { createApp } from 'vue'
import {
  useSwipeBack,
  EDGE_ZONE_PX,
  SWIPE_THRESHOLD_PX,
  isHorizontalScroller,
  startsInsideHorizontalScroller,
  startsInsideNoSwipeZone,
} from '../useSwipeBack'

// Capacitor-Plattform pro Test umschaltbar (vi.hoisted, da vi.mock hochgezogen wird).
const platform = vi.hoisted(() => ({ native: true }))
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => platform.native },
}))

/** Touch-Event bauen - jsdom kennt keinen TouchEvent-Konstruktor, daher manuell. */
function touch(type: string, x: number, y: number): Event {
  const e = new Event(type, { bubbles: true, cancelable: true })
  const point = { clientX: x, clientY: y }
  Object.assign(e, { touches: [point], changedTouches: [point] })
  return e
}

/** Komplette Geste: Start bei (sx,sy), Ende bei (ex,ey). */
function swipe(sx: number, sy: number, ex: number, ey: number) {
  window.dispatchEvent(touch('touchstart', sx, sy))
  window.dispatchEvent(touch('touchend', ex, ey))
}

/** Horizontal scrollbaren Container mit Kind anlegen (jsdom macht kein Layout). */
function makeScroller(scrollable: boolean): { scroller: HTMLElement; child: HTMLElement } {
  const scroller = document.createElement('div')
  scroller.style.overflowX = 'auto'
  Object.defineProperty(scroller, 'scrollWidth', { value: scrollable ? 500 : 200, configurable: true })
  Object.defineProperty(scroller, 'clientWidth', { value: 200, configurable: true })
  const child = document.createElement('div')
  scroller.appendChild(child)
  document.body.appendChild(scroller)
  return { scroller, child }
}

/** Composable in einem Mini-Component mounten, damit onMounted/onUnmounted feuern. */
function mountSwipeBack(back: () => void) {
  const app = createApp({ setup() { useSwipeBack(back); return () => null } })
  app.mount(document.createElement('div'))
  return { unmount: () => app.unmount() }
}

describe('useSwipeBack - DOM-/Integrationspfad', () => {
  let back: Mock<() => void>
  let harness: { unmount: () => void } | null

  beforeEach(() => {
    platform.native = true
    back = vi.fn<() => void>()
    harness = null
  })

  afterEach(() => {
    harness?.unmount()
    document.body.innerHTML = ''
  })

  it('navigiert zurueck bei qualifizierendem Edge-Swipe nach rechts', () => {
    harness = mountSwipeBack(back)
    swipe(5, 100, 5 + SWIPE_THRESHOLD_PX + 20, 105) // Start am Rand, weit nach rechts, fast horizontal
    expect(back).toHaveBeenCalledTimes(1)
  })

  it('ignoriert Swipe, der nicht am linken Rand beginnt (Carousel-Schutz)', () => {
    harness = mountSwipeBack(back)
    swipe(EDGE_ZONE_PX + 50, 100, EDGE_ZONE_PX + 50 + SWIPE_THRESHOLD_PX + 20, 100)
    expect(back).not.toHaveBeenCalled()
  })

  it('ignoriert zu kurzen Swipe (unter Schwelle)', () => {
    harness = mountSwipeBack(back)
    swipe(5, 100, 5 + SWIPE_THRESHOLD_PX - 10, 100)
    expect(back).not.toHaveBeenCalled()
  })

  it('ignoriert ueberwiegend vertikale Bewegung vom Rand (= Scrollen)', () => {
    harness = mountSwipeBack(back)
    swipe(5, 50, 20, 50 + SWIPE_THRESHOLD_PX + 100)
    expect(back).not.toHaveBeenCalled()
  })

  it('bleibt im mobilen Browser (nicht-nativ) komplett inaktiv', () => {
    platform.native = false
    harness = mountSwipeBack(back)
    swipe(5, 100, 5 + SWIPE_THRESHOLD_PX + 20, 105) // selbe qualifizierende Geste
    expect(back).not.toHaveBeenCalled()
  })

  it('entfernt Listener beim Unmount (kein Leak / kein spaeterer Trigger)', () => {
    const h = mountSwipeBack(back)
    h.unmount()
    swipe(5, 100, 5 + SWIPE_THRESHOLD_PX + 20, 105)
    expect(back).not.toHaveBeenCalled()
  })

  it('ignoriert Swipe, der auf einem horizontalen Scroller beginnt (Carousel-Schutz)', () => {
    harness = mountSwipeBack(back)
    const { child } = makeScroller(true)
    // touchstart vom Scroller-Kind (bubbelt zu window, target = Kind), touchend an window
    child.dispatchEvent(touch('touchstart', 5, 100))
    window.dispatchEvent(touch('touchend', 5 + SWIPE_THRESHOLD_PX + 20, 105))
    expect(back).not.toHaveBeenCalled()
  })
})

describe('useSwipeBack - Scroller-Erkennung', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('erkennt einen tatsaechlich horizontal scrollbaren Container', () => {
    const { scroller } = makeScroller(true)
    expect(isHorizontalScroller(scroller)).toBe(true)
  })

  it('ignoriert overflow-x:auto, das nicht ueberlaeuft (nicht scrollbar)', () => {
    const { scroller } = makeScroller(false)
    expect(isHorizontalScroller(scroller)).toBe(false)
  })

  it('ignoriert Element ohne horizontalen Overflow', () => {
    const el = document.createElement('div')
    Object.defineProperty(el, 'scrollWidth', { value: 500, configurable: true })
    Object.defineProperty(el, 'clientWidth', { value: 200, configurable: true })
    document.body.appendChild(el) // overflowX default 'visible'
    expect(isHorizontalScroller(el)).toBe(false)
  })

  it('findet den Scroller auch ueber Vorfahren des Touch-Targets', () => {
    const { child } = makeScroller(true)
    expect(startsInsideHorizontalScroller(child)).toBe(true)
  })

  it('liefert false ausserhalb jedes Scrollers und bei null', () => {
    const loose = document.createElement('div')
    document.body.appendChild(loose)
    expect(startsInsideHorizontalScroller(loose)).toBe(false)
    expect(startsInsideHorizontalScroller(null)).toBe(false)
  })
})

describe('startsInsideNoSwipeZone', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('erkennt das markierte Element selbst', () => {
    const el = document.createElement('div')
    el.setAttribute('data-no-swipe', '')
    document.body.appendChild(el)
    expect(startsInsideNoSwipeZone(el)).toBe(true)
  })

  it('erkennt die Markierung an einem Vorfahren', () => {
    const zone = document.createElement('div')
    zone.setAttribute('data-no-swipe', '')
    const child = document.createElement('span')
    zone.appendChild(child)
    document.body.appendChild(zone)
    expect(startsInsideNoSwipeZone(child)).toBe(true)
  })

  it('liefert false ohne Markierung und bei null', () => {
    const loose = document.createElement('div')
    document.body.appendChild(loose)
    expect(startsInsideNoSwipeZone(loose)).toBe(false)
    expect(startsInsideNoSwipeZone(null)).toBe(false)
  })
})

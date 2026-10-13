// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { scrollToElement } from '../smoothScroll'

let now = 0
let frames: FrameRequestCallback[] = []

function target(top: number, margin = '0px', height = 100) {
  const el = document.createElement('div')
  el.style.scrollMarginTop = margin
  el.getBoundingClientRect = () => ({ top: top - window.scrollY, height, bottom: 0, left: 0, right: 0, width: 0, x: 0, y: 0, toJSON: () => ({}) })
  document.body.appendChild(el)
  return el
}

function runFrames(ms: number) {
  const end = now + ms
  while (now < end && frames.length) {
    now += 16
    const run = frames; frames = []
    run.forEach(f => f(now))
  }
}

describe('scrollToElement', () => {
  beforeEach(() => {
    now = 0; frames = []
    vi.spyOn(performance, 'now').mockImplementation(() => now)
    vi.stubGlobal('requestAnimationFrame', (f: FrameRequestCallback) => { frames.push(f); return frames.length })
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
    window.scrollTo = vi.fn((_x: number | ScrollToOptions, y?: number) => { Object.defineProperty(window, 'scrollY', { value: y ?? 0, configurable: true }) }) as never
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
  })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); document.body.innerHTML = '' })

  it('glides to the element below its scroll margin within the duration, not in one jump', () => {
    const el = target(2000, '112px')
    scrollToElement(el)
    runFrames(100)
    const midway = window.scrollY
    expect(midway).toBeGreaterThan(0)
    expect(midway).toBeLessThan(1888)
    runFrames(600)
    expect(window.scrollY).toBe(1888)
  })

  it('centre: the element ends up in the middle of the viewport', () => {
    Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true })
    const el = target(2000, '0px', 100)
    scrollToElement(el, { block: 'center' })
    runFrames(700)
    // 2000 + 50 - 400
    expect(window.scrollY).toBe(1650)
  })

  it('reduced motion: one jump, no animation', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const el = target(2000)
    scrollToElement(el)
    expect(window.scrollY).toBe(2000)
    expect(frames.length).toBe(0)
  })
})

/**
 * Scrolls the page to an element in a fixed, short time, the same on every browser (native
 * smooth scrolling takes as long as the browser likes). Honours the element's scroll-margin-top
 * (the sticky header) and prefers-reduced-motion, where it jumps.
 */
const DURATION_MS = 450

/** Fast start, soft landing */
const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3)

export function scrollToElement(el: HTMLElement, { block = 'start' }: { block?: 'start' | 'center' } = {}): void {
  const rect = el.getBoundingClientRect()
  const margin = parseFloat(getComputedStyle(el).scrollMarginTop) || 0
  const target = Math.max(0, Math.round(block === 'center'
    ? window.scrollY + rect.top + rect.height / 2 - window.innerHeight / 2
    : window.scrollY + rect.top - margin))

  const reduced = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const from = window.scrollY
  if (reduced || from === target) {
    window.scrollTo(0, target)
    return
  }
  const start = performance.now()
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / DURATION_MS)
    window.scrollTo(0, Math.round(from + (target - from) * easeOutCubic(t)))
    if (t < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

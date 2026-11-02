// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { pagerNavTarget } from '../useTabPager'
import { SWIPE_THRESHOLD_PX } from '../useSwipeBack'

const CAR_TABS = ['/cars', '/charging-providers']
const CONTEXT_TABS = ['/dashboard', '/logs']

describe('pagerNavTarget', () => {
  it('Wisch nach links -> rechter Nachbar', () => {
    expect(pagerNavTarget(-SWIPE_THRESHOLD_PX, 0, CONTEXT_TABS)).toBe('/logs')
    expect(pagerNavTarget(-200, 0, CAR_TABS)).toBe('/charging-providers')
  })

  it('Wisch nach rechts -> linker Nachbar', () => {
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX, 1, CONTEXT_TABS)).toBe('/dashboard')
    expect(pagerNavTarget(200, 1, CAR_TABS)).toBe('/cars')
  })

  it('kein Nachbar in Wischrichtung -> null', () => {
    // schon auf dem rechten Tab, weiter nach links gibt es nichts
    expect(pagerNavTarget(-200, 1, CAR_TABS)).toBeNull()
    // schon auf dem linken Tab, weiter nach rechts gibt es nichts
    expect(pagerNavTarget(200, 0, CAR_TABS)).toBeNull()
  })

  it('unter der Schwelle -> null (kein versehentliches Umschalten)', () => {
    expect(pagerNavTarget(-(SWIPE_THRESHOLD_PX - 1), 0, CAR_TABS)).toBeNull()
    expect(pagerNavTarget(SWIPE_THRESHOLD_PX - 1, 1, CAR_TABS)).toBeNull()
    expect(pagerNavTarget(0, 0, CAR_TABS)).toBeNull()
  })
})

describe('useTabPager', () => {
  // Regression: der Monats-Wisch tauscht den berührten Knoten noch vor touchend aus; das Ende
  // kam dann nie am Pager an und die Seite blieb verschoben stehen.
  it('lässt los, auch wenn der berührte Knoten während des Wischs aus dem DOM fällt', async () => {
    const { ref, nextTick } = await import('vue')
    const { useTabPager } = await import('../useTabPager')
    const host = document.createElement('div')
    const child = document.createElement('div')
    host.appendChild(child)
    document.body.appendChild(host)
    const target = ref<HTMLElement | null>(null)
    const pager = useTabPager(target, ref(0), ref(CONTEXT_TABS), () => {})
    target.value = host
    await nextTick()
    const touch = (type: string, x: number) => {
      const e = new Event(type, { bubbles: true, cancelable: true }) as unknown as TouchEvent
      Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: x, clientY: 100 }] })
      child.dispatchEvent(e)
    }
    touch('touchstart', 100)
    touch('touchmove', 200)
    expect(pager.dragging.value).toBe(true)
    host.removeChild(child)
    touch('touchend', 200)
    expect(pager.dragging.value).toBe(false)
    expect(pager.dragX.value).toBe(0)
  })

  // Regression: beim Loslassen sprang der Track auf 0 zurück, während der Router noch prüfte, und lief
  // erst danach zum Nachbarn: ein kurzes Zurückfedern, dann der Wechsel.
  it('hält den Track an der Fingerposition, bis die Route gewechselt hat, und läuft dann mit dem Fingertempo weiter', async () => {
    const { ref, nextTick } = await import('vue')
    const { useTabPager } = await import('../useTabPager')
    const host = document.createElement('div')
    document.body.appendChild(host)
    // Handybreite: 200 px gewischt, 190 px Restweg
    host.getBoundingClientRect = () => ({ width: 390 }) as DOMRect
    const target = ref<HTMLElement | null>(host)
    const index = ref(0)
    let resolveNav: () => void = () => {}
    const pager = useTabPager(target, index, ref(CONTEXT_TABS), () => new Promise<void>(r => { resolveNav = r }))
    await nextTick()
    const touch = (type: string, x: number, t: number) => {
      const e = new Event(type, { bubbles: true, cancelable: true }) as unknown as TouchEvent
      Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: x, clientY: 100 }] })
      Object.defineProperty(e, 'timeStamp', { value: t })
      host.dispatchEvent(e)
    }
    touch('touchstart', 300, 0)
    touch('touchmove', 250, 16)
    touch('touchmove', 100, 48)
    touch('touchend', 100, 56)
    expect(pager.dragging.value).toBe(false)
    expect(pager.held.value).toBe(true)
    expect(pager.dragX.value).toBe(-200)
    index.value = 1
    await nextTick()
    expect(pager.dragX.value).toBe(0)
    expect(pager.held.value).toBe(false)
    // 150 px in 32 ms: schneller Wisch, kürzer als ohne Schwung
    expect(pager.releaseMs.value).toBeLessThan(320)
    resolveNav()
  })

  async function swipeLeft(navigate: (to: string) => unknown, index = { value: 0 } as { value: number }) {
    const { ref, nextTick } = await import('vue')
    const { useTabPager } = await import('../useTabPager')
    const host = document.createElement('div')
    document.body.appendChild(host)
    const indexRef = ref(index.value)
    const pager = useTabPager(ref<HTMLElement | null>(host), indexRef, ref(CONTEXT_TABS), navigate)
    await nextTick()
    const touch = (type: string, x: number) => {
      const e = new Event(type, { bubbles: true, cancelable: true }) as unknown as TouchEvent
      Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: x, clientY: 100 }] })
      host.dispatchEvent(e)
    }
    touch('touchstart', 300)
    touch('touchmove', 100)
    touch('touchend', 100)
    return { pager, indexRef }
  }

  it('springt zurück, wenn die Navigation scheitert (Guard, Abbruch)', async () => {
    const { pager } = await swipeLeft(() => Promise.resolve(new Error('navigation aborted')))
    await new Promise(r => setTimeout(r))
    expect(pager.held.value).toBe(false)
    expect(pager.dragX.value).toBe(0)
  })

  it('hält weiter, wenn die Navigation fertig ist, bevor der Tab-Index nachzieht', async () => {
    const { pager, indexRef } = await swipeLeft(() => Promise.resolve(undefined))
    await new Promise(r => setTimeout(r))
    expect(pager.held.value).toBe(true)
    expect(pager.dragX.value).toBe(-200)
    indexRef.value = 1
    expect(pager.held.value).toBe(false)
    expect(pager.dragX.value).toBe(0)
  })
})

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
})

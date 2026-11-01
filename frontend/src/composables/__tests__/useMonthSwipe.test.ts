// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { ref, nextTick } from 'vue'
import { monthSwipeTarget, useMonthSwipe, type SwipeSide } from '../useMonthSwipe'

describe('monthSwipeTarget', () => {
  it('nach rechts zum vorigen, nach links zum nächsten Ziel, erst ab der Schwelle', () => {
    expect(monthSwipeTarget(80, true, true)).toBe('prev')
    expect(monthSwipeTarget(-80, true, true)).toBe('next')
    expect(monthSwipeTarget(79, true, true)).toBeNull()
  })

  it('ohne Ziel in Wischrichtung nichts', () => {
    expect(monthSwipeTarget(200, false, true)).toBeNull()
    expect(monthSwipeTarget(-200, true, false)).toBeNull()
  })
})

describe('useMonthSwipe', () => {
  async function setup(has = { prev: true, next: true }) {
    const el = document.createElement('div')
    document.body.appendChild(el)
    const target = ref<HTMLElement | null>(null)
    const picked: SwipeSide[] = []
    const swipe = useMonthSwipe(target, () => has, s => picked.push(s))
    target.value = el
    await nextTick()
    const touch = (type: string, x: number, y = 100) => {
      const e = new Event(type, { bubbles: true, cancelable: true })
      Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: x, clientY: y }] })
      el.dispatchEvent(e)
    }
    return { swipe, picked, touch }
  }

  it('Karte geht mit, beim Loslassen über der Schwelle wird gewählt und zurückgesetzt', async () => {
    const { swipe, picked, touch } = await setup()
    touch('touchstart', 100); touch('touchmove', 200)
    expect(swipe.dragX.value).toBe(100)
    touch('touchend', 200)
    expect(picked).toEqual(['prev'])
    expect(swipe.dragX.value).toBe(0)
    expect(swipe.dragging.value).toBe(false)
  })

  it('ohne Ziel zäh und ohne Wahl', async () => {
    const { swipe, picked, touch } = await setup({ prev: false, next: true })
    touch('touchstart', 100); touch('touchmove', 300)
    expect(swipe.dragX.value).toBe(50)
    touch('touchend', 300)
    expect(picked).toEqual([])
  })

  it('senkrechte Geste bleibt beim Browser', async () => {
    const { swipe, picked, touch } = await setup()
    touch('touchstart', 100, 100); touch('touchmove', 110, 200)
    expect(swipe.dragging.value).toBe(false)
    touch('touchend', 110)
    expect(picked).toEqual([])
  })
})

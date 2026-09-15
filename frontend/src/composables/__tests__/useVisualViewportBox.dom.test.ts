// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick } from 'vue'
import { useVisualViewportBox } from '../useVisualViewportBox'

class FakeVisualViewport extends EventTarget {
  offsetTop = 0
  height = 800
}

const mount = () => {
  let box!: ReturnType<typeof useVisualViewportBox>
  const app = createApp({ setup() { box = useVisualViewportBox(); return () => null } })
  app.mount(document.createElement('div'))
  return { box, app }
}

describe('useVisualViewportBox', () => {
  afterEach(() => { delete (window as any).visualViewport })

  it('ohne Visual-Viewport-API bleibt die Box leer', () => {
    const { box } = mount()
    expect(box.value).toBeNull()
  })

  it('folgt Versatz und Hoehe des sichtbaren Ausschnitts (Tastatur, Adressleiste)', async () => {
    const vv = new FakeVisualViewport()
    ;(window as any).visualViewport = vv
    const { box } = mount()
    expect(box.value).toEqual({ top: 0, height: 800 })
    vv.offsetTop = 120; vv.height = 420
    vv.dispatchEvent(new Event('resize'))
    await nextTick()
    expect(box.value).toEqual({ top: 120, height: 420 })
    vv.offsetTop = 60
    vv.dispatchEvent(new Event('scroll'))
    await nextTick()
    expect(box.value?.top).toBe(60)
  })

  it('raeumt seine Listener beim Unmount ab', () => {
    const vv = new FakeVisualViewport()
    ;(window as any).visualViewport = vv
    const { box, app } = mount()
    app.unmount()
    vv.offsetTop = 99
    vv.dispatchEvent(new Event('resize'))
    expect(box.value?.top).toBe(0)
  })
})

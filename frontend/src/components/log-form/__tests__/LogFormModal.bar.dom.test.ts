// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

vi.mock('../../log-wizard/LogWizard.vue', () => ({ default: defineComponent({ setup: (_, { expose }) => { expose({ hasInput: true }); return () => h('div') } }) }))
import LogFormModal from '../LogFormModal.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mountMinimized() {
  const events: boolean[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(LogFormModal, { minimizable: true, minimized: true, 'onUpdate:minimized': (v: boolean) => events.push(v) }) })
  app.use(i18n)
  app.mount(host)
  await nextTick(); await nextTick()
  return events
}

const touch = (type: string, y: number, t: number) => {
  const e = new Event(type, { bubbles: true, cancelable: true }) as any
  e.touches = type === 'touchend' ? [] : [{ clientX: 100, clientY: y }]
  e.changedTouches = [{ clientX: 100, clientY: y }]
  Object.defineProperty(e, 'timeStamp', { value: t })
  return e
}
function drag(from: number, to: number, ms: number) {
  const bar = document.querySelector('[data-testid="log-form-bar"]')!
  bar.dispatchEvent(touch('touchstart', from, 0))
  bar.dispatchEvent(touch('touchmove', (from + to) / 2, ms / 2))
  bar.dispatchEvent(touch('touchmove', to, ms))
  bar.dispatchEvent(touch('touchend', to, ms))
}

describe('LogFormModal minimiert', () => {
  it('weit hochgezogen: fährt auf', async () => {
    const events = await mountMinimized()
    drag(800, 500, 600)
    expect(events).toEqual([false])
  })

  it('kurz und langsam hochgezogen: federt zurück zur Leiste', async () => {
    const events = await mountMinimized()
    drag(800, 740, 600)
    expect(events).toEqual([])
  })

  it('das Sheet folgt dem Finger', async () => {
    await mountMinimized()
    const bar = document.querySelector('[data-testid="log-form-bar"]')!
    bar.dispatchEvent(touch('touchstart', 800, 0))
    bar.dispatchEvent(touch('touchmove', 760, 50))
    const panel = document.querySelector<HTMLElement>('[data-testid="log-form-sheet"]')!
    expect(panel.style.transform).toContain('translateY(')
    expect(panel.style.transition).toBe('none')
  })

  it('kurzer schneller Wisch nach oben: fährt auf', async () => {
    const events = await mountMinimized()
    drag(800, 740, 40)
    expect(events).toEqual([false])
  })

  it('nach unten gezogen: nichts', async () => {
    const events = await mountMinimized()
    drag(700, 800, 100)
    expect(events).toEqual([])
  })
})

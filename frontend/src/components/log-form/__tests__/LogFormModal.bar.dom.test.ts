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
  Object.assign(e, { clientX: 100, clientY: y, pointerId: 1, pointerType: 'touch', button: 0 })
  Object.defineProperty(e, 'timeStamp', { value: t })
  return e
}
function drag(from: number, to: number, ms: number) {
  const bar = document.querySelector('[data-testid="log-form-bar"]')!
  bar.dispatchEvent(touch('pointerdown', from, 0))
  bar.dispatchEvent(touch('pointermove', (from + to) / 2, ms / 2))
  bar.dispatchEvent(touch('pointermove', to, ms))
  bar.dispatchEvent(touch('pointerup', to, ms))
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
    bar.dispatchEvent(touch('pointerdown', 800, 0))
    bar.dispatchEvent(touch('pointermove', 760, 50))
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

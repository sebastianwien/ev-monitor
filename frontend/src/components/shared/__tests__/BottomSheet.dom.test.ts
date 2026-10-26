// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import BottomSheet from '../BottomSheet.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(locked: boolean) {
  const closed = { n: 0 }
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({
    render: () => h(BottomSheet, { label: 'Test', locked, onClose: () => { closed.n++ } }, { default: () => h('p', { 'data-testid': 'content' }, 'Inhalt') }),
  }))
  app.mount(el)
  await nextTick()
  return closed
}
// Die Schließ-Animation läuft erst durch, dann meldet das Sheet close
const afterLeave = () => new Promise(r => setTimeout(r, 50))

const backdrop = () => document.querySelector<HTMLElement>('[role="dialog"] > .absolute')!

describe('BottomSheet', () => {
  it('schließt per Klick auf die Abdunklung', async () => {
    const closed = await mount(false)
    backdrop().click()
    await afterLeave()
    expect(closed.n).toBe(1)
  })

  it('bleibt offen, solange es gesperrt ist (laufender Import)', async () => {
    const closed = await mount(true)
    backdrop().click()
    await afterLeave()
    expect(closed.n).toBe(0)
    expect(document.querySelector('[data-testid="content"]')).not.toBeNull()
  })
})

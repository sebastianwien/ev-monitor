// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import Collapse from '../Collapse.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(initialOpen: boolean) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const open = ref(initialOpen)
  app = createApp(defineComponent({
    setup: () => () => h(Collapse, { open: open.value }, () => h('div', { id: 'content' }, 'x')),
  }))
  app.mount(host)
  const inner = () => host.querySelector('#content')!.parentElement!
  const outer = () => inner().parentElement!
  return { open, inner, outer }
}

const raf = () => new Promise<void>(r => requestAnimationFrame(() => r()))

describe('Collapse', () => {
  it('lässt absolut positionierte Inhalte (Dropdown) nach dem Aufklappen überstehen', async () => {
    const { open, inner, outer } = mount(false)
    expect(inner().className).toContain('overflow-hidden')
    open.value = true
    await nextTick(); await raf(); await nextTick()
    expect(inner().className).toContain('overflow-hidden') // während der Animation clippen
    outer().dispatchEvent(new Event('transitionend'))
    await nextTick()
    expect(inner().className).not.toContain('overflow-hidden')
  })

  it('ist von Anfang an offen ohne Clipping', () => {
    const { inner } = mount(true)
    expect(inner().className).not.toContain('overflow-hidden')
  })

  it('clippt wieder, sobald es zuklappt', async () => {
    const { open, inner } = mount(true)
    open.value = false
    await nextTick()
    expect(inner().className).toContain('overflow-hidden')
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import ImportSection from '../ImportSection.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers() })

function mount(initiallyOpen: boolean) {
  const open = ref(initiallyOpen)
  const toggles = ref(0)
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({
    render: () => h(ImportSection, { open: open.value, title: 'Tesla', onToggle: () => { toggles.value++ } }, {
      default: () => h('div', { 'data-testid': 'body' }, 'Inhalt'),
    }),
  }))
  app.mount(el)
  return { el, open, toggles }
}

describe('ImportSection', () => {
  it('mountet den Inhalt erst beim Aufklappen: die Integrationen laden beim Mounten ihren Status', async () => {
    const { el, open } = mount(false)
    expect(el.querySelector('[data-testid="body"]')).toBeNull()
    expect(el.querySelector('button')!.getAttribute('aria-expanded')).toBe('false')

    open.value = true
    await nextTick()
    expect(el.querySelector('[data-testid="body"]')).not.toBeNull()
    expect(el.querySelector('button')!.getAttribute('aria-expanded')).toBe('true')
  })

  it('räumt den Inhalt nach dem Zuklappen ab, erst wenn die Animation durch ist', async () => {
    vi.useFakeTimers()
    const { el, open } = mount(true)
    open.value = false
    await nextTick()
    expect(el.querySelector('[data-testid="body"]')).not.toBeNull()

    vi.advanceTimersByTime(300)
    await nextTick()
    expect(el.querySelector('[data-testid="body"]')).toBeNull()
  })

  it('meldet den Klick auf die Kopfzeile, den Zustand hält die Seite', async () => {
    const { el, toggles } = mount(false)
    el.querySelector('button')!.click()
    await nextTick()
    expect(toggles.value).toBe(1)
  })
})

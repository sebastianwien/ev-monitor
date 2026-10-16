// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, type App } from 'vue'
import ChoiceChip from '../ChoiceChip.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(ChoiceChip, props, () => 'Sparsamste') })
  app.mount(host)
  return host.firstElementChild as HTMLElement
}

describe('ChoiceChip', () => {
  it('an active chip is pressed and carries the class active', () => {
    const el = mount({ active: true })
    expect(el.tagName).toBe('BUTTON')
    expect(el.getAttribute('aria-pressed')).toBe('true')
    expect(el.classList.contains('active')).toBe(true)
    expect(el.classList.contains('btn-3d')).toBe(true)
  })

  it('an inactive chip is not pressed', () => {
    const el = mount({ active: false })
    expect(el.getAttribute('aria-pressed')).toBe('false')
    expect(el.classList.contains('active')).toBe(false)
  })

  it('as a link it keeps the look, without a pressed state', () => {
    const el = mount({ href: '/modelle/Tesla' })
    expect(el.tagName).toBe('A')
    expect(el.getAttribute('href')).toBe('/modelle/Tesla')
    expect(el.hasAttribute('aria-pressed')).toBe(false)
    expect(el.classList.contains('btn-3d')).toBe(true)
  })
})

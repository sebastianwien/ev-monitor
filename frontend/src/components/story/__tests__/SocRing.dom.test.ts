// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { createApp, type App } from 'vue'
import SocRing from '../SocRing.vue'

function mount(props: Record<string, unknown>): { el: HTMLElement; app: App } {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const app = createApp(SocRing, props)
  app.mount(el)
  return { el, app }
}

describe('SocRing', () => {
  it('setzt den SoC als CSS-Variable und als ARIA-Wert', () => {
    const { el, app } = mount({ soc: 63, socStart: 20, limit: 80 })
    const ring = el.querySelector('[role="progressbar"]') as HTMLElement
    expect(ring.getAttribute('aria-valuenow')).toBe('63')
    expect(ring.style.getPropertyValue('--soc')).toBe('63')
    expect(ring.style.getPropertyValue('--soc-start')).toBe('20')
    expect(ring.style.getPropertyValue('--soc-limit')).toBe('80')
    app.unmount()
  })

  it('klemmt Werte auf 0 bis 100', () => {
    const { el, app } = mount({ soc: 140, socStart: -5, limit: null })
    const ring = el.querySelector('[role="progressbar"]') as HTMLElement
    expect(ring.style.getPropertyValue('--soc')).toBe('100')
    expect(ring.style.getPropertyValue('--soc-start')).toBe('0')
    expect(ring.style.getPropertyValue('--soc-limit')).toBe('100')
    app.unmount()
  })
})

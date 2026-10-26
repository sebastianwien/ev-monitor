// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import WorkspaceNav from '../WorkspaceNav.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

let router: ReturnType<typeof createRouter>
async function mount(path: string, props: Record<string, unknown> = {}) {
  const stub = { template: '<div />' }
  router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/:p(.*)', component: stub },
  ] })
  await router.push(path)
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(WorkspaceNav, props)
  app.use(i18n).use(router)
  app.mount(host)
  return host
}

const hrefs = (host: HTMLElement) => [...host.querySelectorAll('a')].map(a => a.getAttribute('href'))
const current = (host: HTMLElement) => host.querySelector('[aria-current="page"]')?.getAttribute('href')

describe('WorkspaceNav', () => {
  it('chips: the four areas, import and model comparison sit in the account menu', async () => {
    const host = await mount('/logs')
    expect(hrefs(host)).toEqual(['/dashboard', '/logs', '/charging-providers', '/cars'])
    expect(current(host)).toBe('/logs')
  })

  it('bar in the navbar: the same four areas with short labels', async () => {
    const host = await mount('/cars', { variant: 'bar' })
    expect([...host.querySelectorAll('a')].map(a => a.textContent!.trim())).toEqual(['Dashboard', 'Ladevorgänge & Trips', 'Ladekarten', 'Fahrzeuge'])
    expect(current(host)).toBe('/cars')
  })

  it('on the story the dashboard chip is the current one', async () => {
    const host = await mount('/story')
    expect(current(host)).toBe('/dashboard')
  })

  it('bar: one underline that moves to the active tab', async () => {
    // jsdom misst nicht: Position und Breite je Reiter aus dem Text ableiten
    const x: Record<string, number> = { Dashboard: 0, 'Ladevorgänge & Trips': 100, Ladekarten: 280, Fahrzeuge: 380 }
    const left = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetLeft')!
    const width = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth')!
    Object.defineProperty(HTMLElement.prototype, 'offsetLeft', { configurable: true, get() { return x[this.textContent!.trim()] ?? 0 } })
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, get() { return 90 } })
    try {
      const host = await mount('/logs', { variant: 'bar' })
      await nextTick()
      const line = host.querySelector('[data-testid="workspace-nav-indicator"]') as HTMLElement
      expect(line.getAttribute('aria-hidden')).toBe('true')
      expect(line.style.transform).toBe('translateX(100px)')
      expect(line.style.width).toBe('90px')

      await router.push('/charging-providers')
      await nextTick(); await nextTick()
      expect(line.style.transform).toBe('translateX(280px)')
      expect(host.querySelectorAll('[data-testid="workspace-nav-indicator"]')).toHaveLength(1)
    } finally {
      Object.defineProperty(HTMLElement.prototype, 'offsetLeft', left)
      Object.defineProperty(HTMLElement.prototype, 'offsetWidth', width)
    }
  })
})

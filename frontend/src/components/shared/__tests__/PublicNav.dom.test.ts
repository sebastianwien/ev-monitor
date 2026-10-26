// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, ref, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'
import PublicNav from '../PublicNav.vue'
import { WORKSPACE_FRAME_HEAD } from '../../../layouts/workspaceFrameHead'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

/** framed: das Gerüst WorkspaceFrame liegt darüber und bringt seinen eigenen Kopf mit */
async function mount(loggedIn: boolean, publicNav: boolean, props: Record<string, unknown> = {}, demo = false, framed = false) {
  setActivePinia(createPinia())
  if (loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1', demoAccount: demo } as never
  }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/modelle', component: { template: '<div />' }, meta: publicNav ? { publicNav: true } : {} },
  ] })
  await router.push('/modelle')
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    components: { PublicNav },
    setup: () => ({ props }),
    template: '<PublicNav v-bind="props"><template #below><div data-testid="below-slot" /></template></PublicNav>',
  })
  app.use(i18n).use(router)
  app.provide(WORKSPACE_FRAME_HEAD, ref(framed))
  app.mount(host)
  return host
}

describe('PublicNav', () => {
  it('member under the workspace frame: the page\'s own nav steps back, the frame\'s nav (home) stays', async () => {
    const page = await mount(true, true, {}, false, true)
    expect(page.querySelector('nav')).toBeNull()
    app?.unmount(); app = null; document.body.innerHTML = ''
    const frame = await mount(true, true, { home: true }, false, true)
    expect(frame.querySelector('[data-testid="public-nav-account"]')).not.toBeNull()
  })

  it('guest never sees the frame: the page keeps its nav even if a frame were provided', async () => {
    const host = await mount(false, true, {}, false, true)
    expect(host.textContent).toContain('Registrieren')
  })

  it('guest: register and login, no dashboard', async () => {
    const host = await mount(false, true)
    expect(host.textContent).toContain('Registrieren')
    expect(host.querySelector('[data-testid="public-nav-dashboard"]')).toBeNull()
  })

  it('member on a publicNav route: dashboard and watt balance instead of register and login', async () => {
    const host = await mount(true, true)
    expect(host.textContent).not.toContain('Registrieren')
    expect(host.textContent).not.toContain('Anmelden')
    expect(host.querySelector('[data-testid="public-nav-dashboard"]')?.getAttribute('href')).toBe('/dashboard')
    expect(host.querySelector('[data-testid="public-nav-watt"]')).not.toBeNull()
  })

  it('member on the home page (home): no link to itself, the profile instead', async () => {
    const host = await mount(true, true, { home: true })
    expect(host.querySelector('[data-testid="public-nav-dashboard"]')).toBeNull()
    expect(host.querySelector('[data-testid="public-nav-settings"]')?.getAttribute('href')).toBe('/settings')
    expect(host.querySelector('[data-testid="public-nav-watt"]')).not.toBeNull()
  })

  it('member at home: the account row opens below the nav with settings, import, model comparison and logout', async () => {
    const host = await mount(true, true, { home: true })
    const trigger = host.querySelector('[data-testid="public-nav-account"]') as HTMLButtonElement
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    trigger.click()
    await nextTick()
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    const row = document.getElementById(trigger.getAttribute('aria-controls')!)!
    expect(row.tagName).toBe('NAV')
    expect([...row.querySelectorAll('a')].map(a => a.getAttribute('href'))).toEqual(['/settings', '/imports', '/modelle'])
    expect(row.querySelector('[data-testid="public-nav-logout"]')).not.toBeNull()
  })

  it('account row: Escape closes it and gives the focus back to the profile button', async () => {
    const host = await mount(true, true, { home: true })
    const trigger = host.querySelector('[data-testid="public-nav-account"]') as HTMLButtonElement
    trigger.click()
    await nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger)
  })

  it('demo account: the account row only offers the model comparison', async () => {
    const host = await mount(true, true, { home: true }, true)
    const trigger = host.querySelector('[data-testid="public-nav-account"]') as HTMLButtonElement
    trigger.click()
    await nextTick()
    const row = document.getElementById(trigger.getAttribute('aria-controls')!)!
    expect([...row.querySelectorAll('a')].map(a => a.getAttribute('href'))).toEqual(['/modelle'])
    expect(row.querySelector('[data-testid="public-nav-logout"]')).toBeNull()
  })

  it('member: feedback button opens the same Tally form as the app header', async () => {
    const host = await mount(true, true)
    expect(host.querySelector('[data-testid="public-nav-feedback"]')?.getAttribute('data-tally-open')).toBe('vGB8XA')
  })

  it('guest: no feedback button', async () => {
    const host = await mount(false, true)
    expect(host.querySelector('[data-testid="public-nav-feedback"]')).toBeNull()
  })

  it('the band below the nav (ticker) sits inside the sticky header', async () => {
    const host = await mount(true, true, { home: true })
    const sticky = host.querySelector('.sticky')!
    expect(sticky.querySelector('[data-testid="below-slot"]')).not.toBeNull()
  })

  it('member on any other route: the nav stays away, the app chrome takes over', async () => {
    const host = await mount(true, false)
    expect(host.querySelector('nav')).toBeNull()
  })

  it('home: the areas only stay in the nav while they fit next to the right group', async () => {
    const width = (name: 'scrollWidth' | 'clientWidth') => Object.getOwnPropertyDescriptor(Element.prototype, name)!
    const scroll = width('scrollWidth'), client = width('clientWidth')
    // jsdom misst nicht: der Slot bräuchte 500 px und hat 400
    Object.defineProperty(Element.prototype, 'scrollWidth', { configurable: true, get: () => 500 })
    Object.defineProperty(Element.prototype, 'clientWidth', { configurable: true, get: () => 400 })
    try {
      setActivePinia(createPinia())
      const auth = useAuthStore()
      auth.token = 'token'
      auth.user = { userId: 'u1' } as never
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/story', component: { template: '<div />' }, meta: { publicNav: true } }] })
      await router.push('/story')
      await router.isReady()
      const fits: boolean[] = []
      const host = document.createElement('div')
      document.body.appendChild(host)
      app = createApp({
        components: { PublicNav },
        setup: () => ({ onFits: (f: boolean) => fits.push(f) }),
        template: '<PublicNav home @nav-fits="onFits"><template #nav><span data-testid="tabs">tabs</span></template></PublicNav>',
      })
      app.use(i18n).use(router)
      app.mount(host)
      await nextTick()
      expect(fits).toEqual([false])
      expect(host.querySelector('[data-testid="tabs"]')!.parentElement!.classList.contains('invisible')).toBe(true)
    } finally {
      Object.defineProperty(Element.prototype, 'scrollWidth', scroll)
      Object.defineProperty(Element.prototype, 'clientWidth', client)
    }
  })
})

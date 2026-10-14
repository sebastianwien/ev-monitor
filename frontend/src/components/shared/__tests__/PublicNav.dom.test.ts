// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'
import PublicNav from '../PublicNav.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(loggedIn: boolean, publicNav: boolean) {
  setActivePinia(createPinia())
  if (loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
  }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/modelle', component: { template: '<div />' }, meta: publicNav ? { publicNav: true } : {} },
  ] })
  await router.push('/modelle')
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PublicNav)
  app.use(i18n).use(router)
  app.mount(host)
  return host
}

describe('PublicNav', () => {
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

  it('member on any other route: the nav stays away, the app chrome takes over', async () => {
    const host = await mount(true, false)
    expect(host.querySelector('nav')).toBeNull()
  })
})

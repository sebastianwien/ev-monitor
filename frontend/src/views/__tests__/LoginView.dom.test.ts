// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'

vi.mock('../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => false } }))
import LoginView from '../LoginView.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mountLogin() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div />' } }] })
  await router.push('/login')
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(LoginView)
  app.use(createPinia()).use(router).use(i18n)
  app.mount(host)
  await nextTick()
  return host
}

describe('LoginView', () => {
  it('offers the mail code as a way in without password', async () => {
    const host = await mountLogin()
    expect(host.querySelector('[data-testid="inline-gate"]')).toBeNull()

    ;(host.querySelector('[data-testid="login-with-code"]') as HTMLButtonElement).click()
    await nextTick()

    expect(host.querySelector('[data-testid="inline-gate"]')).not.toBeNull()
    expect(host.querySelector('input[type="password"]')).toBeNull()
  })
})

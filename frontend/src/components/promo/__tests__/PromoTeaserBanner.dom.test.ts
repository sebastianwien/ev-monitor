// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
import api from '../../../api/axios'
import PromoTeaserBanner from '../PromoTeaserBanner.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; sessionStorage.clear(); localStorage.clear() })

async function mount(opts: { loggedIn?: boolean; premium?: boolean } = {}) {
  setActivePinia(createPinia())
  if (opts.loggedIn !== false) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
    auth.isPremium = opts.premium === true
  }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p*', component: { render: () => null } }] })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PromoTeaserBanner)
  app.use(i18n).use(router)
  app.mount(host)
  for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() }
  return host
}

describe('PromoTeaserBanner', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('shows the firstev offer with a link to the prefilled upgrade page', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: { code: 'FIRSTEV', trialDays: 28 } } as never)
    const host = await mount()

    expect(host.textContent).toContain('firstev')
    const link = host.querySelector('a') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/upgrade?code=FIRSTEV')
  })

  it('stays hidden without an offer', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 204, data: '' } as never)
    const host = await mount()

    expect(host.querySelector('[data-testid="promo-banner"]')).toBeNull()
  })

  it('never asks for premium users or guests', async () => {
    await mount({ premium: true })
    await mount({ loggedIn: false })

    expect(api.get).not.toHaveBeenCalled()
  })

  it('stays closed after dismissing', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: { code: 'FIRSTEV', trialDays: 28 } } as never)
    let host = await mount()
    ;(host.querySelector('[data-testid="promo-banner-close"]') as HTMLButtonElement).click()
    await nextTick()
    expect(host.querySelector('[data-testid="promo-banner"]')).toBeNull()

    app?.unmount(); app = null
    host = await mount()
    expect(host.querySelector('[data-testid="promo-banner"]')).toBeNull()
  })
})

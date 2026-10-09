// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { i18n } from '../../../i18n'

vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import { analytics } from '../../../services/analytics'
import PartnerBanner from '../PartnerBanner.vue'
import type { PartnerBannerConfig } from '../../../config/partners'

const FIRSTEV: PartnerBannerConfig = {
  id: 'firstev', active: true, href: 'https://www.firstev.de/', imageUrl: '/partners/firstev.webp',
  alt: 'firstev.de', width: 728, height: 90,
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(partner: PartnerBannerConfig) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PartnerBanner, { partner })
  app.use(i18n)
  app.mount(host)
  return host
}

describe('PartnerBanner', () => {
  it('renders nothing while the partner is inactive', () => {
    const host = mount({ ...FIRSTEV, active: false })
    expect(host.querySelector('a')).toBeNull()
  })

  it('links to the partner with our UTM, followable, in a new tab', () => {
    const host = mount(FIRSTEV)
    const link = host.querySelector('a') as HTMLAnchorElement
    const url = new URL(link.href)

    expect(url.origin + url.pathname).toBe('https://www.firstev.de/')
    expect(url.searchParams.get('utm_source')).toBe('ev-monitor')
    expect(link.rel).toBe('noopener')
    expect(link.target).toBe('_blank')
    const img = link.querySelector('img') as HTMLImageElement
    expect(img.getAttribute('alt')).toBe('firstev.de')
    expect(img.getAttribute('width')).toBe('728')
  })

  it('tracks the click', () => {
    const host = mount(FIRSTEV)
    const link = host.querySelector('a') as HTMLAnchorElement
    link.addEventListener('click', e => e.preventDefault())
    link.click()
    expect(analytics.track).toHaveBeenCalledWith('partner_banner_click', { partner: 'firstev' })
  })
})

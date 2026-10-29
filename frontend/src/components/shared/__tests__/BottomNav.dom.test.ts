// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import BottomNav from '../BottomNav.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(path: string, moreOpen = false) {
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)', component: stub }] })
  await router.push(path)
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(BottomNav, { moreOpen, wattBalance: 844, isDemo: false })
  app.use(i18n).use(router)
  app.mount(host)
  return host
}

const active = (host: HTMLElement) => [...host.querySelectorAll('[data-active]')].map(el => el.getAttribute('href') ?? 'more')

describe('BottomNav', () => {
  it('marks the tab of the current route, the story counts as start', async () => {
    expect(active(await mount('/logs'))).toEqual(['/logs'])
    app?.unmount()
    expect(active(await mount('/story'))).toEqual(['/dashboard'])
  })

  it('open more sheet: only more looks active, the route keeps aria-current', async () => {
    const host = await mount('/logs', true)
    expect(active(host)).toEqual(['more'])
    expect(host.querySelector('[aria-current="page"]')?.getAttribute('href')).toBe('/logs')
    expect(host.querySelector('[aria-expanded="true"]')).not.toBeNull()
  })
})

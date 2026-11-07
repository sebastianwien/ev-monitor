// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import BottomNav from '../BottomNav.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

let closed = 0
async function mount(path: string, moreOpen = false) {
  closed = 0
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)', component: stub }] })
  await router.push(path)
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(BottomNav, { moreOpen, onCloseMore: () => { closed++ } })
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

  it('open more sheet: another tab or record closes it', async () => {
    const host = await mount('/logs', true)
    host.querySelector<HTMLElement>('a[href="/dashboard"]')!.click()
    host.querySelector<HTMLElement>('a[href="/logs"]')!.click()
    host.querySelector<HTMLElement>(`button[aria-label]`)!.click()
    expect(closed).toBe(3)
  })

  it('stations tab sits where watt was, active on /stations; watt moved to the more sheet', async () => {
    const host = await mount('/stations')
    expect(active(host)).toEqual(['/stations'])
    expect(host.querySelector('a[href="/stations"]')?.textContent).toContain('Säulen')
    expect(host.querySelector('a[href="/coins/history"]')).toBeNull()
  })

  it('closed more sheet: a tab does not ask to close it', async () => {
    const host = await mount('/logs')
    host.querySelector<HTMLElement>('a[href="/dashboard"]')!.click()
    expect(closed).toBe(0)
  })
})

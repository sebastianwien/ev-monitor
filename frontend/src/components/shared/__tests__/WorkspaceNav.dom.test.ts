// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import WorkspaceNav from '../WorkspaceNav.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(path: string) {
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/:p(.*)', component: stub },
  ] })
  await router.push(path)
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(WorkspaceNav)
  app.use(i18n).use(router)
  app.mount(host)
  return host
}

const hrefs = (host: HTMLElement) => [...host.querySelectorAll('a')].map(a => a.getAttribute('href'))
const current = (host: HTMLElement) => host.querySelector('[aria-current="page"]')?.getAttribute('href')

describe('WorkspaceNav', () => {
  it('the same chips as the story: four areas, then import and model comparison', async () => {
    const host = await mount('/logs')
    expect(hrefs(host)).toEqual(['/dashboard', '/logs', '/charging-providers', '/cars', '/imports', '/modelle'])
    expect(current(host)).toBe('/logs')
  })

  it('on the story the dashboard chip is the current one', async () => {
    const host = await mount('/story')
    expect(current(host)).toBe('/dashboard')
  })
})

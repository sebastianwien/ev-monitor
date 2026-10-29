// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import MoreSheet from '../MoreSheet.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(isNative: boolean) {
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)', component: stub }] })
  await router.push('/dashboard')
  await router.isReady()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(MoreSheet, { feedbackMailto: 'mailto:x', isDemo: false, isNative })
  app.use(i18n).use(router).use(createPinia())
  app.mount(host)
  await nextTick()
  return document.body
}

const hrefs = (root: HTMLElement) => [...root.querySelectorAll('a')].map(a => a.getAttribute('href'))

describe('MoreSheet', () => {
  it('native app: legal links in the sheet (store review)', async () => {
    expect(hrefs(await mount(true))).toEqual(expect.arrayContaining(['/impressum', '/datenschutz', '/agb']))
  })

  it('web: no legal links, they sit in the page footer', async () => {
    const links = hrefs(await mount(false))
    expect(links).toContain('/cars')
    expect(links).not.toContain('/impressum')
  })
})

// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, h, nextTick, ref, type App, type Ref } from 'vue'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'

/* Rotation gemockt: der Test prüft nur, wann der Ticker die Rotation anhält. */
let paused: Ref<boolean>
vi.mock('../../../composables/useTickerRotation', () => ({
  dwellMs: () => 5000,
  useTickerRotation: (opts: { paused: Ref<boolean> }) => {
    paused = opts.paused
    return { index: ref(0), next: vi.fn(), prev: vi.fn(), restart: vi.fn() }
  },
}))
vi.mock('../../../composables/useTickerItems', () => ({
  VARIANT_ICON: {},
  useTickerItems: () => ({
    items: ref([
      { type: 'STAT', variant: 'money', text: 'Erster Eintrag', target: '/modelle' },
      { type: 'STAT', variant: 'money', text: 'Zweiter Eintrag' },
    ]),
    fetchTicker: () => Promise.resolve(),
  }),
}))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))

import LeaderboardTicker from '../LeaderboardTicker.vue'

let app: App | null = null
beforeEach(() => { localStorage.clear(); localStorage.setItem('ticker-has-items', 'true') })
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount() {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(LeaderboardTicker, { docked: true }) })
  app.use(createI18n({ legacy: false, locale: 'de', messages: { de: {} }, missingWarn: false, fallbackWarn: false }))
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { render: () => null } }] }))
  app.mount(host)
  await nextTick()
  return host
}

function touch(el: Element, type: 'touchstart' | 'touchend', x: number) {
  const e = new Event(type, { bubbles: true }) as Event & Record<string, unknown>
  e.touches = type === 'touchstart' ? [{ clientX: x }] : []
  e.changedTouches = [{ clientX: x }]
  el.dispatchEvent(e)
}

describe('LeaderboardTicker - Berührung auf dem Handy', () => {
  it('hält an, solange der Finger aufliegt, und läuft nach dem Loslassen weiter', async () => {
    const host = await mount()
    const viewport = host.querySelector('.ticker-viewport')!

    touch(viewport, 'touchstart', 100)
    expect(paused.value).toBe(true)

    touch(viewport, 'touchend', 100)
    expect(paused.value).toBe(false)
  })

  it('macht den Text zum Link, wenn der Eintrag ein Ziel hat', async () => {
    const host = await mount()
    const link = host.querySelector('.ticker-viewport a')
    expect(link?.getAttribute('href')).toBe('/modelle')
  })
})

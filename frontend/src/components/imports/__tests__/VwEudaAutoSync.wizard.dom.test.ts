// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'

const routes = {
  status: [] as unknown[],
  entitlement: { entitled: true, viaTrial: true, trialEndsAt: '2026-10-21' },
  smartcar: { connected: false, carId: null as string | null },
}
vi.mock('../../../api/axios', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/eu-data-act/status') return Promise.resolve({ data: routes.status })
      if (url === '/subscription/eu-data-act-autosync') return Promise.resolve({ data: routes.entitlement })
      if (url === '/smartcar/status') return Promise.resolve({ data: routes.smartcar })
      return Promise.resolve({ data: [] })
    }),
    post: vi.fn(() => Promise.resolve({ data: {
      carId: 'car-1', brand: 'skoda', email: 'max@example.com', vin: null,
      status: 'ACTIVE', lastSuccessAt: null, historyImportedAt: null, lastError: null,
    } })),
    delete: vi.fn(),
  },
}))
import VwEudaAutoSync from '../VwEudaAutoSync.vue'

const car = { id: 'car-1', brand: 'SKODA', model: 'Enyaq', year: 2024, status: 'ACTIVE' } as never

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => {
  vi.clearAllMocks()
  routes.status = []
  routes.entitlement = { entitled: true, viaTrial: true, trialEndsAt: '2026-10-21' }
  routes.smartcar = { connected: false, carId: null }
})

async function mount() {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p*', component: { render: () => null } }] })
  app = createApp(defineComponent({ render: () => h(VwEudaAutoSync, { cars: [car] }) }))
  app.use(createPinia()).use(i18n).use(router)
  app.mount(el)
  await flush()
  return el
}
async function flush() { for (let i = 0; i < 5; i++) await nextTick(); await new Promise(r => setTimeout(r, 0)); await nextTick() }
const byId = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`)

describe('VwEudaAutoSync Wizard', () => {
  it('Schritt 1: Entscheidung ohne Formular, Erklärung eingeklappt, Trial-Satz sichtbar', async () => {
    const el = await mount()
    expect(byId(el, 'euda-step-decide')).not.toBeNull()
    expect(byId(el, 'euda-start')).not.toBeNull()
    expect(byId(el, 'euda-trial-hint')).not.toBeNull()
    expect(el.querySelector('#euda-password')).toBeNull()
    expect(byId(el, 'euda-explainer')).toBeNull()
  })

  it('Details aufklappen zeigt die Erklärung', async () => {
    const el = await mount()
    byId(el, 'euda-details-toggle')!.click()
    await nextTick()
    expect(byId(el, 'euda-explainer')).not.toBeNull()
  })

  it('Schritt 2: Formular nach Klick auf Verbinden, Zurück führt zu Schritt 1', async () => {
    const el = await mount()
    byId(el, 'euda-start')!.click()
    await nextTick()
    expect(byId(el, 'euda-step-connect')).not.toBeNull()
    expect(el.querySelector('#euda-password')).not.toBeNull()
    expect(byId(el, 'euda-step-decide')).toBeNull()
    byId(el, 'euda-back')!.click()
    await nextTick()
    expect(byId(el, 'euda-step-decide')).not.toBeNull()
  })

  it('Smartcar-Hinweis nur, wenn das Fahrzeug über Smartcar verbunden ist', async () => {
    let el = await mount()
    byId(el, 'euda-start')!.click()
    await nextTick()
    expect(byId(el, 'euda-smartcar-note')).toBeNull()
    app!.unmount(); app = null; document.body.innerHTML = ''

    routes.smartcar = { connected: true, carId: 'car-1' }
    el = await mount()
    byId(el, 'euda-start')!.click()
    await nextTick()
    expect(byId(el, 'euda-smartcar-note')).not.toBeNull()
  })

  it('Schritt 2 verlinkt den öffentlichen Anmelde-Code auf GitHub', async () => {
    const el = await mount()
    byId(el, 'euda-start')!.click()
    await nextTick()
    const link = byId(el, 'euda-open-source') as HTMLAnchorElement | null
    expect(link).not.toBeNull()
    expect(link!.getAttribute('href')).toBe('https://github.com/sebastianwien/ev-monitor/blob/main/backend/src/main/java/com/evmonitor/application/vweuda/VwEudaLoginClient.java')
    expect(link!.getAttribute('rel')).toContain('noopener')
  })

  it('Schritt 3: nach Verbinden Erfolgsmeldung und Status-Karte', async () => {
    const el = await mount()
    byId(el, 'euda-start')!.click()
    await nextTick()
    const email = el.querySelector<HTMLInputElement>('#euda-email')!
    const pw = el.querySelector<HTMLInputElement>('#euda-password')!
    email.value = 'max@example.com'; email.dispatchEvent(new Event('input'))
    pw.value = 'geheim'; pw.dispatchEvent(new Event('input'))
    await nextTick()
    el.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()
    expect(byId(el, 'euda-connected')).not.toBeNull()
    expect(byId(el, 'euda-disconnect')).not.toBeNull()
    expect(el.querySelector('#euda-password')).toBeNull()
  })

  it('Bestehende Verbindung startet direkt in der Status-Karte', async () => {
    routes.status = [{ carId: 'car-1', brand: 'skoda', email: 'max@example.com', vin: null, status: 'ACTIVE', lastSuccessAt: null, historyImportedAt: null, lastError: null }]
    const el = await mount()
    expect(byId(el, 'euda-step-decide')).toBeNull()
    expect(byId(el, 'euda-disconnect')).not.toBeNull()
  })
})

// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('@/api/axios', () => ({ default: { get: vi.fn(), patch: vi.fn() } }))
import api from '@/api/axios'
import PricelessLogsModal from '../PricelessLogsModal.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(props: { carId: string | null; open: boolean }) {
  const pinia = createPinia(); setActivePinia(pinia)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(PricelessLogsModal, props)
  app.use(pinia)
  app.use(i18n)
  app.mount(host)
}

const flush = async () => { for (let i = 0; i < 5; i++) { await nextTick(); await Promise.resolve() } }

describe('PricelessLogsModal', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockImplementation(async (url: string) => ({ data: url.startsWith('/logs/priceless') ? [] : [] }) as never)
  })

  it('lädt die Liste auch, wenn es schon geöffnet angelegt wird (Deep-Link)', async () => {
    mount({ carId: 'car-b', open: true })
    await flush()

    expect(vi.mocked(api.get).mock.calls.map((c) => c[0])).toContain('/logs/priceless?carId=car-b')
  })

  it('lädt nichts, solange es geschlossen ist', async () => {
    mount({ carId: 'car-b', open: false })
    await flush()

    expect(vi.mocked(api.get).mock.calls.map((c) => c[0])).not.toContain('/logs/priceless?carId=car-b')
  })
})

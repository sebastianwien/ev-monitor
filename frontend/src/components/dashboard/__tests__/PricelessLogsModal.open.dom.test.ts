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

function mount(props: { carId: string | null; open: boolean; onUpdated?: () => void }) {
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

  it('"Weiß ich nicht mehr" markiert die Ladung und nimmt sie aus der Liste', async () => {
    const row = { id: 'l1', loggedAt: '2026-10-12T10:00:00Z', kwhCharged: 28.5, kwhAtVehicle: null, costEur: null, chargingType: 'DC', dataSource: 'USER_LOGGED' }
    vi.mocked(api.get).mockImplementation(async (url: string) => ({ data: url.startsWith('/logs/priceless') ? [row, { ...row, id: 'l2' }] : [] }) as never)
    vi.mocked(api.patch).mockResolvedValue({ data: {} } as never)
    const onUpdated = vi.fn()
    mount({ carId: 'car-b', open: true, onUpdated })
    await flush()

    ;(document.querySelector('[data-testid="priceless-unknown"]') as HTMLButtonElement).click()
    await flush()

    expect(api.patch).toHaveBeenCalledWith('/logs/l1/gap', { field: 'PRICE' })
    expect(document.querySelectorAll('[data-testid="priceless-row"]')).toHaveLength(1)
    expect(onUpdated).toHaveBeenCalled()
  })

  it('lädt nichts, solange es geschlossen ist', async () => {
    mount({ carId: 'car-b', open: false })
    await flush()

    expect(vi.mocked(api.get).mock.calls.map((c) => c[0])).not.toContain('/logs/priceless?carId=car-b')
  })
})

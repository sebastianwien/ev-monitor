// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))
import api from '../../../api/axios'
import EditLogModal from '../EditLogModal.vue'

const log = {
  id: 'log-1', carId: 'car-1', kwhCharged: 52.85, costEur: 14.8, costExchangeRate: null, costCurrency: null,
  chargeDurationMinutes: 292, geohash: null, odometerKm: null, maxChargingPowerKw: null, socAfterChargePercent: null,
  socBeforeChargePercent: null, kwhAtVehicle: null, loggedAt: '2026-09-11T10:06:00', routeType: null, tireType: null,
  chargingType: 'AC' as const, isPublicCharging: false, cpoName: null, chargingProviderId: null, dataSource: 'WALLBOX_GOE',
}

let app: App | null = null
const deleted: string[] = []
const closed = { count: 0 }

afterEach(() => {
  app?.unmount()
  app = null
  document.body.innerHTML = ''
  deleted.length = 0
  closed.count = 0
})
beforeEach(() => vi.clearAllMocks())

function mountModal() {
  const Host = defineComponent({
    render: () => h(EditLogModal, {
      log,
      onDeleted: (id: string) => deleted.push(id),
      onClose: () => { closed.count++ },
    }),
  })
  app = createApp(Host)
  app.use(i18n).use(createPinia())
  app.directive('haptic', {})
  app.mount(document.body.appendChild(document.createElement('div')))
}

const q = (sel: string) => document.body.querySelector<HTMLElement>(sel)

describe('EditLogModal - Ladevorgang loeschen', () => {
  it('loescht erst nach Bestaetigung im Sheet (kein window.confirm) und meldet die Log-ID', async () => {
    ;(api.delete as any).mockResolvedValue({})
    mountModal()
    await nextTick()

    q('[data-testid="edit-delete"]')!.click()
    await nextTick()
    expect(api.delete).not.toHaveBeenCalled()
    expect(q('[data-testid="edit-delete-confirm"]')).not.toBeNull()

    q('[data-testid="edit-delete-confirm"]')!.click()
    await vi.waitFor(() => expect(deleted).toEqual(['log-1']))
    expect(api.delete).toHaveBeenCalledWith('/logs/log-1')
    expect(closed.count).toBe(0)
  })

  it('zeigt bei fehlgeschlagenem Loeschen eine Meldung und bleibt offen', async () => {
    ;(api.delete as any).mockRejectedValue(new Error('500'))
    mountModal()
    await nextTick()

    q('[data-testid="edit-delete"]')!.click()
    await nextTick()
    q('[data-testid="edit-delete-confirm"]')!.click()
    await vi.waitFor(() => expect(document.body.textContent).toContain('Löschen fehlgeschlagen'))
    expect(deleted).toEqual([])
    expect(q('[data-testid="edit-log-modal"]')).not.toBeNull()
  })

  it('Abbrechen der Nachfrage loescht nichts', async () => {
    mountModal()
    await nextTick()
    q('[data-testid="edit-delete"]')!.click()
    await nextTick()
    q('[data-testid="edit-delete-cancel"]')!.click()
    await nextTick()
    expect(q('[data-testid="edit-delete-confirm"]')).toBeNull()
    expect(api.delete).not.toHaveBeenCalled()
  })
})

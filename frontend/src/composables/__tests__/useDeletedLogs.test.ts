import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
import api from '../../api/axios'
import { useDeletedLogs } from '../useDeletedLogs'
import { useLogsRefreshStore } from '../../stores/logsRefresh'

const a = { id: 'a', loggedAt: '2026-09-24T11:35:00', kwhCharged: 1.61, kwhAtVehicle: null, dataSource: 'API_UPLOAD', deletedAt: '2026-09-24T19:53:20' }
const b = { ...a, id: 'b', loggedAt: '2026-09-23T11:29:00' }

describe('useDeletedLogs', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(api.get).mockResolvedValue({ data: [a, b] })
    vi.mocked(api.post).mockResolvedValue({})
    vi.mocked(api.delete).mockResolvedValue({})
  })

  it('lädt den Papierkorb des Autos', async () => {
    const { logs, load } = useDeletedLogs(ref('car-1'))
    await load()

    expect(api.get).toHaveBeenCalledWith('/logs/deleted', { params: { carId: 'car-1' } })
    expect(logs.value.map(l => l.id)).toEqual(['a', 'b'])
  })

  it('zeigt den Ladezustand, bis die Liste da ist', async () => {
    let resolve!: (v: unknown) => void
    vi.mocked(api.get).mockReturnValueOnce(new Promise(r => { resolve = r }) as never)
    const { loading, load } = useDeletedLogs(ref('car-1'))
    const done = load()

    expect(loading.value).toBe(true)
    resolve({ data: [a] })
    await done
    expect(loading.value).toBe(false)
  })

  it('ohne Auto kein Request', async () => {
    const { logs, load } = useDeletedLogs(ref(null))
    await load()

    expect(api.get).not.toHaveBeenCalled()
    expect(logs.value).toEqual([])
  })

  it('Wiederherstellen entfernt den Eintrag und stößt den Feed-Refresh an', async () => {
    const { logs, load, restore } = useDeletedLogs(ref('car-1'))
    await load()
    await restore('a')

    expect(api.post).toHaveBeenCalledWith('/logs/a/restore')
    expect(logs.value.map(l => l.id)).toEqual(['b'])
    expect(useLogsRefreshStore().version).toBe(1)
  })

  it('Wiederherstellen mit 409 markiert die Zeile und behält den Eintrag', async () => {
    vi.mocked(api.post).mockRejectedValueOnce({ response: { status: 409 } })
    const { logs, load, restore, failed } = useDeletedLogs(ref('car-1'))
    await load()
    await restore('a')

    expect(logs.value.map(l => l.id)).toEqual(['a', 'b'])
    expect(failed.value.get('a')).toBe('restore')
  })

  it('fehlgeschlagenes Entfernen wird als eigener Fehler markiert', async () => {
    vi.mocked(api.delete).mockRejectedValueOnce({ response: { status: 500 } })
    const { logs, load, purge, failed } = useDeletedLogs(ref('car-1'))
    await load()
    await purge('b')

    expect(logs.value.map(l => l.id)).toEqual(['a', 'b'])
    expect(failed.value.get('b')).toBe('purge')
  })

  it('Endgültig entfernen löscht hart und entfernt den Eintrag', async () => {
    const { logs, load, purge } = useDeletedLogs(ref('car-1'))
    await load()
    await purge('b')

    expect(api.delete).toHaveBeenCalledWith('/logs/b/purge')
    expect(logs.value.map(l => l.id)).toEqual(['a'])
  })
})

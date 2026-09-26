import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLogsRefreshStore } from '../../stores/logsRefresh'

vi.mock('../../api/axios', () => ({ default: { delete: vi.fn(), post: vi.fn() } }))
import api from '../../api/axios'
import { useLogUndo } from '../useLogUndo'

describe('useLogUndo', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    vi.mocked(api.delete).mockResolvedValue({})
    vi.mocked(api.post).mockResolvedValue({})
    useLogUndo().dismiss()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('löscht und bietet Rückgängig an', async () => {
    const { deleteWithUndo, pending } = useLogUndo()
    await deleteWithUndo('log-1', vi.fn())

    expect(api.delete).toHaveBeenCalledWith('/logs/log-1')
    expect(pending.value?.id).toBe('log-1')
  })

  it('Rückgängig stellt wieder her und ruft den Refresh', async () => {
    const onChanged = vi.fn()
    const { deleteWithUndo, undo, pending } = useLogUndo()
    await deleteWithUndo('log-1', onChanged)
    await undo()

    expect(api.post).toHaveBeenCalledWith('/logs/log-1/restore')
    expect(onChanged).toHaveBeenCalled()
    expect(useLogsRefreshStore().version).toBe(1)
    expect(pending.value).toBeNull()
  })

  it('Angebot verschwindet nach 10 Sekunden', async () => {
    const { deleteWithUndo, pending } = useLogUndo()
    await deleteWithUndo('log-1', vi.fn())
    vi.advanceTimersByTime(10_000)

    expect(pending.value).toBeNull()
  })

  it('fehlgeschlagenes Löschen bietet kein Rückgängig an und wirft weiter', async () => {
    vi.mocked(api.delete).mockRejectedValueOnce(new Error('500'))
    const { deleteWithUndo, pending } = useLogUndo()

    await expect(deleteWithUndo('log-1', vi.fn())).rejects.toThrow()
    expect(pending.value).toBeNull()
  })
})

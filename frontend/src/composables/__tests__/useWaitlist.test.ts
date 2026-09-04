import { describe, it, expect, vi, beforeEach } from 'vitest'
import waitlistService from '../../api/waitlistService'
import { useWaitlist } from '../useWaitlist'

vi.mock('../../api/waitlistService')
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

beforeEach(() => vi.clearAllMocks())

describe('useWaitlist', () => {
  it('uebernimmt den Status beim Laden', async () => {
    vi.mocked(waitlistService.getStatus).mockResolvedValue({ onWaitlist: true, since: '2026-09-01T10:00:00' })
    const w = useWaitlist('XPENG_AUTOSYNC')
    await w.load()

    expect(waitlistService.getStatus).toHaveBeenCalledWith('XPENG_AUTOSYNC')
    expect(w.onWaitlist.value).toBe(true)
    expect(w.loaded.value).toBe(true)
  })

  it('schluckt Ladefehler und bleibt beim Default (nicht eingetragen)', async () => {
    vi.mocked(waitlistService.getStatus).mockRejectedValue(new Error('boom'))
    const w = useWaitlist('XPENG_AUTOSYNC')
    await w.load()

    expect(w.onWaitlist.value).toBe(false)
    expect(w.loaded.value).toBe(true)
  })

  it('traegt den User ein und setzt onWaitlist', async () => {
    vi.mocked(waitlistService.join).mockResolvedValue({ onWaitlist: true, since: '2026-09-04T12:00:00' })
    const w = useWaitlist('XPENG_AUTOSYNC')
    await w.join()

    expect(waitlistService.join).toHaveBeenCalledWith('XPENG_AUTOSYNC')
    expect(w.onWaitlist.value).toBe(true)
    expect(w.error.value).toBeNull()
  })

  it('meldet einen Fehler beim Eintragen', async () => {
    vi.mocked(waitlistService.join).mockRejectedValue(new Error('boom'))
    const w = useWaitlist('XPENG_AUTOSYNC')
    await w.join()

    expect(w.onWaitlist.value).toBe(false)
    expect(w.error.value).toBe('waitlist.error')
  })

  it('traegt den User wieder aus', async () => {
    vi.mocked(waitlistService.leave).mockResolvedValue(undefined)
    const w = useWaitlist('XPENG_AUTOSYNC')
    w.onWaitlist.value = true
    await w.leave()

    expect(waitlistService.leave).toHaveBeenCalledWith('XPENG_AUTOSYNC')
    expect(w.onWaitlist.value).toBe(false)
  })
})

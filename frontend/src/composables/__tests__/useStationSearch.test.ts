import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../api/axios'
import { useStationSearch } from '../useStationSearch'

const enbw = { name: 'EnBW', known: true, maxAcKw: null, maxDcKw: 300, fastCharging: true, chargePoints: 4,
  address: 'Am Fuchsgraben 1, 91586 Lichtenau', plugTypes: ['CCS'], registerId: 1, geohash: 'u0z87g2' }

describe('useStationSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockResolvedValue({ data: [enbw] } as never)
  })
  afterEach(() => vi.useRealTimers())

  it('fragt erst ab drei Zeichen, entprellt, mit dem Suchtext als q', async () => {
    const s = useStationSearch()
    s.query.value = 'En'
    await nextTick(); await vi.runAllTimersAsync()
    expect(api.get).not.toHaveBeenCalled()
    s.query.value = 'EnBW Lichtenau'
    await nextTick(); await vi.runAllTimersAsync()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(api.get).toHaveBeenCalledWith('/charging-provider-tariffs/cpos/search-stations', { params: { q: 'EnBW Lichtenau' } })
    expect(s.matches.value).toEqual([enbw])
    expect(s.searched.value).toBe(true)
  })

  it('verwirft die Antwort einer veralteten Anfrage', async () => {
    let resolveFirst!: (v: unknown) => void
    vi.mocked(api.get)
      .mockImplementationOnce(() => new Promise(r => { resolveFirst = r }))
      .mockResolvedValueOnce({ data: [enbw] } as never)
    const s = useStationSearch()
    s.query.value = 'EnBW'
    await nextTick(); await vi.runAllTimersAsync()
    s.query.value = 'EnBW Lichtenau'
    await nextTick(); await vi.runAllTimersAsync()
    expect(s.matches.value).toEqual([enbw])
    resolveFirst({ data: [{ ...enbw, name: 'Alt' }] })
    await vi.runAllTimersAsync()
    expect(s.matches.value).toEqual([enbw])
  })

  it('Fehler und zu kurzer Text leeren die Liste statt zu werfen', async () => {
    vi.mocked(api.get).mockRejectedValueOnce(new Error('429'))
    const s = useStationSearch()
    s.query.value = 'EnBW Lichtenau'
    await nextTick(); await vi.runAllTimersAsync()
    expect(s.matches.value).toEqual([])
    expect(s.searched.value).toBe(true)
    expect(s.loading.value).toBe(false)
    s.query.value = 'En'
    await nextTick()
    expect(s.searched.value).toBe(false)
  })
})

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { useLocationSearch } from '../useLocationSearch'

describe('useLocationSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    globalThis.fetch = vi.fn().mockResolvedValue({ json: async () => [{ place_id: 1, lat: '52.5', lon: '13.4', display_name: 'Berlin' }] }) as any
  })
  afterEach(() => vi.useRealTimers())

  it('fragt erst ab drei Zeichen und mit Verzögerung an', async () => {
    const s = useLocationSearch()
    s.query.value = 'Be'
    await nextTick(); vi.advanceTimersByTime(400)
    expect(fetch).not.toHaveBeenCalled()
    s.query.value = 'Berlin'
    await nextTick(); vi.advanceTimersByTime(400); await vi.runAllTimersAsync()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toContain('q=Berlin')
    expect(s.suggestions.value).toHaveLength(1)
  })

  it('select liefert Koordinaten als Zahlen und übernimmt den Namen', async () => {
    const s = useLocationSearch()
    const picked = s.select({ place_id: 1, lat: '52.5', lon: '13.4', display_name: 'Berlin' })
    expect(picked).toEqual({ latitude: 52.5, longitude: 13.4, name: 'Berlin' })
    expect(s.query.value).toBe('Berlin')
    expect(s.selectedName.value).toBe('Berlin')
    expect(s.suggestions.value).toEqual([])
  })

  it('ein Netzfehler leert die Vorschläge statt zu werfen', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('offline'))
    const s = useLocationSearch()
    s.query.value = 'Rasthof'
    await nextTick(); await vi.runAllTimersAsync()
    expect(s.suggestions.value).toEqual([])
  })
})

import { describe, it, expect, vi, afterEach } from 'vitest'
import { fetchDestinationForecast } from './destinationForecast'

afterEach(() => vi.unstubAllGlobals())

describe('fetchDestinationForecast', () => {
  it('liefert heute und morgen und schickt nur einen auf 5 Stellen gekürzten Ort', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ daily: { temperature_2m_min: [3.4, 1.2], temperature_2m_max: [11.6, 8.1], precipitation_probability_max: [20, 70] } }),
    })
    vi.stubGlobal('fetch', fetchMock)
    const f = await fetchDestinationForecast('u0yjjd6jkq')
    expect(f).toEqual([{ minC: 3, maxC: 12, rain: false }, { minC: 1, maxC: 8, rain: true }])
    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.host).toBe('api.open-meteo.com')
    expect(Number(url.searchParams.get('latitude')).toFixed(2)).toBe(url.searchParams.get('latitude'))
  })

  it('zeigt keine Prognose bei fehlenden Werten und fragt nach einem Fehler erneut', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ daily: { temperature_2m_min: [null, 1], temperature_2m_max: [9, 8], precipitation_probability_max: [0, 0] } }) })
    vi.stubGlobal('fetch', fetchMock)
    expect(await fetchDestinationForecast('u2abcdef')).toBeNull()
    expect(await fetchDestinationForecast('u2abcdef')).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('gibt bei Fehler null zurück', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await fetchDestinationForecast('u1hcvz8x')).toBeNull()
  })
})

import { describe, it, expect, vi, afterEach } from 'vitest'
import { fetchDestinationForecast, weatherKind } from './destinationForecast'

afterEach(() => vi.unstubAllGlobals())

const daily = (code: number[], gusts: number[] = [10, 10]) => ({
  ok: true,
  json: async () => ({ daily: { weather_code: code, temperature_2m_min: [3.4, 1.2], temperature_2m_max: [11.6, 8.1], wind_gusts_10m_max: gusts } }),
})

describe('weatherKind', () => {
  it.each([
    [0, 'sun'], [1, 'sun'], [2, 'partly'], [3, 'cloudy'], [45, 'cloudy'], [48, 'cloudy'],
    [51, 'rain'], [61, 'rain'], [67, 'rain'], [95, 'rain'], [99, 'rain'],
    [80, 'showers'], [82, 'showers'],
    [71, 'snow'], [77, 'snow'], [85, 'snow'], [86, 'snow'],
  ])('WMO %i ist %s', (code, kind) => {
    expect(weatherKind(code, 20)).toBe(kind)
  })

  it('sehr windig ab 40 km/h Böen, außer bei Niederschlag', () => {
    expect(weatherKind(1, 40)).toBe('wind')
    expect(weatherKind(3, 75)).toBe('wind')
    expect(weatherKind(1, 39)).toBe('sun')
    expect(weatherKind(61, 80)).toBe('rain')
    expect(weatherKind(73, 80)).toBe('snow')
  })
})

describe('fetchDestinationForecast', () => {
  it('liefert heute und morgen und schickt nur einen auf 5 Stellen gekürzten Ort', async () => {
    const fetchMock = vi.fn().mockResolvedValue(daily([2, 81]))
    vi.stubGlobal('fetch', fetchMock)
    const f = await fetchDestinationForecast('u0yjjd6jkq')
    expect(f).toEqual([{ minC: 3, maxC: 12, kind: 'partly' }, { minC: 1, maxC: 8, kind: 'showers' }])
    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.host).toBe('api.open-meteo.com')
    expect(Number(url.searchParams.get('latitude')).toFixed(2)).toBe(url.searchParams.get('latitude'))
  })

  it('zeigt keine Prognose bei fehlenden Werten und fragt nach einem Fehler erneut', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ daily: { weather_code: [0, null], temperature_2m_min: [1, 1], temperature_2m_max: [9, 8], wind_gusts_10m_max: [0, 0] } }) })
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

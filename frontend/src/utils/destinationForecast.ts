import geohash from 'ngeohash'

export type WeatherKind = 'sun' | 'partly' | 'cloudy' | 'rain' | 'showers' | 'snow' | 'wind'
export interface DayForecast { minC: number; maxC: number; kind: WeatherKind }

/** Ab diesen Böen (km/h, Beaufort 8) gilt ein trockener Tag als sehr windig. */
const WIND_GUST_KMH = 60

/** WMO-Wettercode des Tages plus Böen: Niederschlag geht vor Wind, Wind vor Wolken. */
export function weatherKind(code: number, gustKmh: number): WeatherKind {
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow'
  if (code >= 80 && code <= 82) return 'showers'
  if (code >= 51) return 'rain'
  if (gustKmh >= WIND_GUST_KMH) return 'wind'
  if (code <= 1) return 'sun'
  if (code === 2) return 'partly'
  return 'cloudy'
}
const cache = new Map<string, Promise<DayForecast[] | null>>()

/**
 * Prognose für heute und morgen am Ziel, direkt von Open-Meteo. Der Ort geht nur
 * auf Geohash-5 gekürzt raus (rund 5 km), nie die genaue Fahrtspur.
 */
export function fetchDestinationForecast(endGeohash: string): Promise<DayForecast[] | null> {
  const cell = endGeohash.slice(0, 5)
  let hit = cache.get(cell)
  if (!hit) {
    hit = load(cell).then(f => { if (!f) cache.delete(cell); return f })
    cache.set(cell, hit)
  }
  return hit
}

async function load(cell: string): Promise<DayForecast[] | null> {
  try {
    const { latitude, longitude } = geohash.decode(cell)
    const url = new URL('https://api.open-meteo.com/v1/forecast')
    url.searchParams.set('latitude', latitude.toFixed(2))
    url.searchParams.set('longitude', longitude.toFixed(2))
    url.searchParams.set('daily', 'weather_code,temperature_2m_min,temperature_2m_max,wind_gusts_10m_max')
    url.searchParams.set('timezone', 'auto')
    url.searchParams.set('forecast_days', '2')
    const res = await fetch(url.toString())
    if (!res.ok) return null
    const d = (await res.json()).daily
    const days = [0, 1]
    if (days.some(i => d?.weather_code?.[i] == null || d?.temperature_2m_min?.[i] == null || d?.temperature_2m_max?.[i] == null)) return null
    return days.map(i => ({
      minC: Math.round(d.temperature_2m_min[i]),
      maxC: Math.round(d.temperature_2m_max[i]),
      kind: weatherKind(d.weather_code[i], d.wind_gusts_10m_max?.[i] ?? 0),
    }))
  } catch {
    return null
  }
}

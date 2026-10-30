import geohash from 'ngeohash'

export interface DayForecast { minC: number; maxC: number; rain: boolean }

/** Ab dieser Regenwahrscheinlichkeit (Prozent) nennt die Karte Regen. */
const RAIN_PERCENT = 50
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
    url.searchParams.set('daily', 'temperature_2m_min,temperature_2m_max,precipitation_probability_max')
    url.searchParams.set('timezone', 'auto')
    url.searchParams.set('forecast_days', '2')
    const res = await fetch(url.toString())
    if (!res.ok) return null
    const d = (await res.json()).daily
    const days = [0, 1]
    if (days.some(i => d?.temperature_2m_min?.[i] == null || d?.temperature_2m_max?.[i] == null)) return null
    return days.map(i => ({
      minC: Math.round(d.temperature_2m_min[i]),
      maxC: Math.round(d.temperature_2m_max[i]),
      rain: (d.precipitation_probability_max[i] ?? 0) >= RAIN_PERCENT,
    }))
  } catch {
    return null
  }
}

import api from './axios'

/** Temperatur-Ausblick am Heimladeort: die nächsten 7 Tage gegen die letzte Woche, Grad Celsius. */
export interface WeatherOutlookResponse {
  recentMeanC: number
  aheadMeanC: number
  /** Negativ heißt kälter. */
  deltaC: number
  recentDays: number
  aheadDays: number
  /** Tiefst- und Höchstwert der kommenden Tage; fehlt bei älterem Backend oder ohne Werte. */
  aheadMinC?: number | null
  aheadMaxC?: number | null
}

export const weatherService = {
  /** null ohne Heimladeort oder ohne Wetterdaten (204). */
  async getOutlook(): Promise<WeatherOutlookResponse | null> {
    const res = await api.get<WeatherOutlookResponse>('/weather/outlook')
    return res.status === 204 ? null : res.data
  },
}

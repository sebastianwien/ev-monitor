import api from './axios'

/** Temperatur-Ausblick am Heimladeort: kommende Tage gegen die letzte Woche, Grad Celsius. */
export interface WeatherOutlookResponse {
  recentMeanC: number
  aheadMeanC: number
  /** Negativ heißt kälter. */
  deltaC: number
  recentDays: number
  aheadDays: number
}

export const weatherService = {
  /** null ohne Heimladeort oder ohne Wetterdaten (204). */
  async getOutlook(): Promise<WeatherOutlookResponse | null> {
    const res = await api.get<WeatherOutlookResponse>('/weather/outlook')
    return res.status === 204 ? null : res.data
  },
}

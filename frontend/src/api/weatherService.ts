import api from './axios'

/** Temperatur-Ausblick am Heimladeort: die nächsten 7 Tage gegen die letzte Woche, Grad Celsius. */
export interface WeatherOutlookResponse {
  recentMeanC: number
  aheadMeanC: number
  /** Negativ heißt kälter. */
  deltaC: number
  recentDays: number
  aheadDays: number
  /** Tage der Vorhersage mit Wetterlage; fehlt bei älterem Backend. */
  days?: WeatherOutlookDay[]
}

/** Ein Tag der Vorhersage, Ortszeit. code ist der WMO-Wettercode, gustKmh die stärkste Böe. */
export interface WeatherOutlookDay {
  date: string
  minC: number
  maxC: number
  code: number
  gustKmh: number | null
}

export const weatherService = {
  /** null ohne Heimladeort oder ohne Wetterdaten (204). */
  async getOutlook(): Promise<WeatherOutlookResponse | null> {
    const res = await api.get<WeatherOutlookResponse>('/weather/outlook')
    return res.status === 204 ? null : res.data
  },
}

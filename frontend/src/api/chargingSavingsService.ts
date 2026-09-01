import api from './axios'
import type { ChargingSavings } from '../components/dashboard/chargingSavings'

export default {
  /**
   * Heimlade-Ersparnis des angemeldeten Nutzers.
   *
   * @returns null, wenn Heim- oder Vergleichspreis unbekannt sind (204) oder der Tarif
   *          die Kachel nicht enthaelt (403). Beide Faelle sind kein Fehler - die Kachel
   *          zeigt dann ihren Leerzustand.
   */
  async get(): Promise<ChargingSavings | null> {
    try {
      const response = await api.get<ChargingSavings>('/stats/charging-savings')
      return response.status === 204 ? null : response.data
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 403 || status === 401) return null
      throw error
    }
  },

  /** Wallbox samt Installation. null loescht den Wert wieder. */
  async saveInvestment(investmentEur: number | null): Promise<void> {
    await api.patch('/users/me/home-investment', { investmentEur })
  },
}

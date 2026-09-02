import api from './axios'
import type { ChargingSavings } from '../components/dashboard/chargingSavings'

export default {
  /**
   * Heimlade-Ersparnis des angemeldeten Nutzers.
   *
   * Die beiden leeren Faelle sind verschieden und muessen unterscheidbar bleiben:
   * 204 heisst "berechtigt, aber es fehlt ein Preis" - dann zeigt die Kachel ihren
   * Leerzustand. 403 heisst "Tarif enthaelt die Kachel nicht" - dann bleibt sie weg.
   */
  async get(): Promise<{ savings: ChargingSavings | null; entitled: boolean }> {
    try {
      const response = await api.get<ChargingSavings>('/stats/charging-savings')
      return { savings: response.status === 204 ? null : response.data, entitled: true }
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 403 || status === 401) return { savings: null, entitled: false }
      throw error
    }
  },

  /** Wallbox samt Installation. null loescht den Wert wieder. */
  async saveInvestment(investmentEur: number | null): Promise<void> {
    await api.patch('/users/me/home-investment', { investmentEur })
  },
}

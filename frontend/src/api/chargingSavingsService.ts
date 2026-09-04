import api from './axios'
import type { ChargingSavings } from '../components/dashboard/chargingSavings'

export default {
  /**
   * Heimlade-Ersparnis des angemeldeten Nutzers.
   *
   * Die leeren Faelle sind verschieden und muessen unterscheidbar bleiben:
   * - 204: berechtigt, aber keine relevante Kachel (kein Heimpreis oder zu wenig
   *   Heimladung) - dann wird nichts angezeigt.
   * - 403: kein Zugang mehr (Trial vorbei, kein bezahlter Tarif) - {@code locked},
   *   dann zeigt das Dashboard den Upsell-Teaser statt nichts.
   * - 401: Zustand unbekannt (kein Token) - weder Kachel noch Teaser.
   *
   * {@code viaTrial}/{@code trialEndsAt} kommen nur im 200-Fall mit und steuern den
   * Retention-Hinweis: sie sind gesetzt, solange der Zugang ausschliesslich am Trial
   * haengt (nicht bei zahlenden Nutzern).
   */
  async get(): Promise<{
    savings: ChargingSavings | null
    entitled: boolean
    locked: boolean
    viaTrial: boolean
    trialEndsAt: string | null
  }> {
    try {
      const response = await api.get<ChargingSavings & { viaTrial?: boolean; trialEndsAt?: string | null }>(
        '/stats/charging-savings',
      )
      if (response.status === 204) {
        return { savings: null, entitled: true, locked: false, viaTrial: false, trialEndsAt: null }
      }
      return {
        savings: response.data,
        entitled: true,
        locked: false,
        viaTrial: response.data.viaTrial ?? false,
        trialEndsAt: response.data.trialEndsAt ?? null,
      }
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 403) return { savings: null, entitled: false, locked: true, viaTrial: false, trialEndsAt: null }
      if (status === 401) return { savings: null, entitled: false, locked: false, viaTrial: false, trialEndsAt: null }
      throw error
    }
  },

  /** Wallbox samt Installation. null loescht den Wert wieder. */
  async saveInvestment(investmentEur: number | null): Promise<void> {
    await api.patch('/users/me/home-investment', { investmentEur })
  },
}

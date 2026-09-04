import api from './axios'

/**
 * Sichtbarkeit der Dashboard-Kacheln des angemeldeten Nutzers.
 *
 * Serverseitig gehalten, nicht im localStorage: das Ausblenden ist eine Aussage ueber den
 * Nutzer, nicht ueber das Geraet - sonst taucht die Kachel auf jedem neuen Geraet wieder auf.
 */
export default {
  async get(): Promise<{ savingsCardDismissed: boolean }> {
    const response = await api.get<{ savingsCardDismissed: boolean }>('/users/me/dashboard-preferences')
    return { savingsCardDismissed: response.data.savingsCardDismissed }
  },

  async setSavingsCardDismissed(dismissed: boolean): Promise<void> {
    await api.patch('/users/me/dashboard-preferences', { savingsCardDismissed: dismissed })
  },
}

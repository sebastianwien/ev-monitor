import api from './axios'

export interface WaitlistStatus {
  onWaitlist: boolean
  since: string | null
}

/**
 * Opt-in auf Feature-Wartelisten ("benachrichtige mich, sobald verfuegbar").
 * `feature` ist ein serverseitig validierter Enum-Name (z.B. 'XPENG_AUTOSYNC').
 */
export const waitlistService = {
  async getStatus(feature: string): Promise<WaitlistStatus> {
    const resp = await api.get(`/waitlist/${feature}`)
    return resp.data
  },

  async join(feature: string): Promise<WaitlistStatus> {
    const resp = await api.post(`/waitlist/${feature}`)
    return resp.data
  },

  async leave(feature: string): Promise<void> {
    await api.delete(`/waitlist/${feature}`)
  },
}

export default waitlistService

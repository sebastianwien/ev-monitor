import api from './axios'

export type PolestarResult = 'ACTIVE' | 'NOT_SHARED' | 'WAITLIST' | 'DISCONNECTED'

export interface PolestarConnection {
  id: string | null
  carId: string
  vin: string | null
  status: 'ACTIVE' | 'DISCONNECTED' | null
  connectedAt: string | null
  lastEventAt: string | null
  snapshotCount: number
  result: PolestarResult
}

/** Konto-Kennung, die der Nutzer im Polestar Data Portal unter "Shared access" freigibt. */
export const POLESTAR_SHARE_ACCOUNT = 'polestar@ev-monitor.net'
export const POLESTAR_PORTAL_URL = 'https://data-portal.polestar.com'

export default {
  async getConnections(): Promise<PolestarConnection[]> {
    const resp = await api.get('/polestar/connections')
    return resp.data
  },

  async connect(carId: string, vin: string): Promise<PolestarConnection> {
    const resp = await api.post('/polestar/connect', { carId, vin })
    return resp.data
  },

  async disconnect(id: string): Promise<void> {
    await api.delete(`/polestar/connections/${id}`)
  },
}

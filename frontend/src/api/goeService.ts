import api from './axios'

export interface GoeConnection {
  id: string
  serial: string
  displayName: string | null
  active: boolean
  carState: number
  lastPollError: string | null
  carStateLabel: string
}

export const CAR_STATE_LABELS: Record<number, string> = {
  1: 'Bereit',
  2: 'Lädt',
  3: 'Wartet auf Auto',
  4: 'Fertig',
  5: 'Fehler',
}

export default {
  async getConnections(): Promise<GoeConnection[]> {
    const resp = await api.get('/api/goe/connections')
    return resp.data.map((c: any) => ({
      ...c,
      carStateLabel: CAR_STATE_LABELS[c.carState] ?? 'Unbekannt',
    }))
  },

  async connect(serial: string, apiKey: string, carId: string, displayName: string): Promise<GoeConnection> {
    const resp = await api.post('/api/goe/connect', { serial, apiKey, carId, displayName })
    return { ...resp.data, carStateLabel: CAR_STATE_LABELS[resp.data.carState] ?? 'Unbekannt' }
  },

  async disconnect(id: string): Promise<void> {
    await api.delete(`/api/goe/connections/${id}`)
  },
}

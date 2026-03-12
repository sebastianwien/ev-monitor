import api from './axios'

export interface GoeConnection {
  id: string
  serial: string
  displayName: string | null
  active: boolean
  carState: number
  lastPollError: string | null
  carStateLabel: string
  tariffCentsPerKwh: number
  geohash: string | null
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
    const resp = await api.get('/goe/connections')
    return resp.data.map((c: any) => ({
      ...c,
      carStateLabel: CAR_STATE_LABELS[c.carState] ?? 'Unbekannt',
    }))
  },

  async connect(serial: string, apiKey: string, carId: string, displayName: string, geohash: string | null = null): Promise<GoeConnection> {
    const resp = await api.post('/goe/connect', { serial, apiKey, carId, displayName, geohash })
    return { ...resp.data, carStateLabel: CAR_STATE_LABELS[resp.data.carState] ?? 'Unbekannt' }
  },

  async disconnect(id: string): Promise<void> {
    await api.delete(`/goe/connections/${id}`)
  },

  async updateTariff(id: string, tariffCentsPerKwh: number): Promise<GoeConnection> {
    const resp = await api.patch(`/goe/connections/${id}/tariff`, { tariffCentsPerKwh })
    return { ...resp.data, carStateLabel: CAR_STATE_LABELS[resp.data.carState] ?? 'Unbekannt' }
  },
}

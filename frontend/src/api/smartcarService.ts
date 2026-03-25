import api from './axios'

export interface SmartcarConnectionStatus {
  connected: boolean
  vehicleName: string | null
  carId: string | null
  vin: string | null
  vehicleState: string | null
  lastCheckedAt: string | null
  lastSoc: number | null
  sessionActive: boolean
  sessionStartedAt: string | null
  sessionEnergyAdded: number | null
}

export default {
  async getStatus(): Promise<SmartcarConnectionStatus> {
    const resp = await api.get('/smartcar/status')
    return resp.data
  },

  async getAuthStartUrl(carId: string): Promise<{ authUrl: string; available: boolean }> {
    const resp = await api.get('/smartcar/auth/start', { params: { carId } })
    return resp.data
  },

  async disconnect(): Promise<void> {
    await api.delete('/smartcar/disconnect')
  },
}

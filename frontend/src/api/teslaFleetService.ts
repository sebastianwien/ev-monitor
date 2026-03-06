import api from './axios'

export interface TeslaConnectionStatus {
  connected: boolean
  vehicleName: string | null
  lastSyncAt: string | null
  autoImportEnabled: boolean
}

export interface TeslaFleetSyncResult {
  logsImported: number
  logsSkipped: number
  vehicleName: string
  message: string
}

export default {
  async getStatus(): Promise<TeslaConnectionStatus> {
    const resp = await api.get('/api/tesla/fleet/status')
    return resp.data
  },

  async getAuthStartUrl(carId: string): Promise<{ authUrl: string; fleetApiConfigured: boolean }> {
    const resp = await api.get('/api/tesla/fleet/auth/start', { params: { carId } })
    return resp.data
  },

  async syncHistory(): Promise<TeslaFleetSyncResult> {
    const resp = await api.post('/api/tesla/fleet/sync-history')
    return resp.data
  },

  async disconnect(): Promise<void> {
    await api.delete('/api/tesla/fleet/disconnect')
  },
}

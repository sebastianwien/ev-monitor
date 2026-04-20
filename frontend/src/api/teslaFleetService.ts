import api from './axios'

export interface TeslaConnectionStatus {
  connected: boolean
  vehicleName: string | null
  carId: string | null
  lastSyncAt: string | null
  autoImportEnabled: boolean
  geocodingInProgress: boolean
  vehicleState: 'asleep' | 'online' | 'charging' | null
  suspendAfterIdleMinutes: number
}

export interface TeslaPairingStatus {
  vin: string
  keyPaired: boolean
  telemetryConfigPushed: boolean
  dataSource?: string
  telemetryConfigPushedAt?: string | null
}

export interface TeslaFleetSyncResult {
  logsImported: number
  logsSkipped: number
  vehicleName: string
  message: string
  importedLogIds: string[]
}

export default {
  async getStatus(): Promise<TeslaConnectionStatus> {
    const resp = await api.get('/tesla/fleet/status')
    return resp.data
  },

  async getAuthStartUrl(carId: string): Promise<{ authUrl: string; fleetApiConfigured: boolean }> {
    const resp = await api.get('/tesla/fleet/auth/start', { params: { carId } })
    return resp.data
  },

  async syncHistory(): Promise<TeslaFleetSyncResult> {
    const resp = await api.post('/tesla/fleet/sync-history')
    return resp.data
  },

  async updateSettings(suspendAfterIdleMinutes: number): Promise<void> {
    await api.patch('/tesla/fleet/settings', { suspendAfterIdleMinutes })
  },

  async disconnect(): Promise<void> {
    await api.delete('/tesla/fleet/disconnect')
  },

  async deleteByIds(ids: string[]): Promise<void> {
    await api.delete('/logs/batch', { data: ids })
  },

  async deleteAllImports(): Promise<void> {
    await api.delete('/import/tesla/delete-all')
  },

  async getPairingStatus(): Promise<TeslaPairingStatus> {
    const resp = await api.get('/tesla/pairing/status')
    return resp.data
  },

  async enableTelemetry(): Promise<{ alreadyEnabled: boolean }> {
    const resp = await api.post('/tesla/pairing/enable-telemetry')
    return resp.data
  },

  async disableTelemetry(): Promise<void> {
    await api.post('/tesla/pairing/disable-telemetry')
  },
}

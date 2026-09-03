import api from './axios'

export interface TeslaConnectionStatus {
  connected: boolean
  vehicleName: string | null
  carId: string | null
  lastSyncAt: string | null
  autoImportEnabled: boolean
  geocodingInProgress: boolean
  vehicleState: 'asleep' | 'online' | 'charging' | null
  /** null = not yet observed at a telemetry config push, not "confirmed absent". */
  locationScopeGranted?: boolean | null
}

export interface TeslaPairingStatus {
  vin: string
  keyPaired: boolean
  telemetryConfigPushed: boolean
  dataSource?: string
  telemetryProfile?: 'CHARGING_ONLY' | 'FULL'
  telemetryConfigPushedAt?: string | null
}

export interface TelemetryRepushResult {
  total: number
  pushed: number
  failed: number
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

  /**
   * Fetches the OAuth authorize URL for carId and redirects the browser to it, or reports why
   * it couldn't. Centralizes the "getAuthStartUrl → check fleetApiConfigured/authUrl → redirect"
   * sequence that TeslaFleetIntegration.vue, TeslaTelemetryPrompt.vue and the reconnect
   * announcement all need - callers still own their own error copy/UI for 'not_configured'.
   */
  async startReconnect(carId: string): Promise<'redirected' | 'not_configured'> {
    const authStart = await this.getAuthStartUrl(carId)
    if (!authStart.fleetApiConfigured || !authStart.authUrl) return 'not_configured'
    window.location.href = authStart.authUrl
    return 'redirected'
  },

  async getAuthStartUrl(carId: string): Promise<{ authUrl: string; fleetApiConfigured: boolean }> {
    const resp = await api.get('/tesla/fleet/auth/start', { params: { carId } })
    return resp.data
  },

  async syncHistory(): Promise<TeslaFleetSyncResult> {
    const resp = await api.post('/tesla/fleet/sync-history')
    return resp.data
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

  /**
   * Admin-only: pushes the current telemetry field set to every vehicle already on telemetry.
   * Needed after the field set changes - an existing config is otherwise never refreshed.
   */
  async repushAllTelemetry(): Promise<TelemetryRepushResult> {
    const resp = await api.post('/tesla/pairing/repush-all-telemetry')
    return resp.data
  },
}

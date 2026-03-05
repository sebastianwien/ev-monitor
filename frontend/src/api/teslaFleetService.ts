import apiClient from './axios';

export interface TeslaFleetAuthStart {
  authUrl: string | null;
  fleetApiConfigured: boolean;
}

export interface TeslaFleetSyncResult {
  logsImported: number;
  logsSkipped: number;
  vehicleName: string;
  message: string;
}

export interface TeslaConnectionStatus {
  connected: boolean;
  vehicleName: string | null;
  lastSyncAt: string | null;
  autoImportEnabled: boolean;
  authType?: string; // 'OWNER_API' | 'FLEET_API'
}

const teslaFleetService = {
  async getStatus(): Promise<TeslaConnectionStatus> {
    const r = await apiClient.get<TeslaConnectionStatus>('/tesla/status');
    return r.data;
  },

  async getAuthStartUrl(): Promise<TeslaFleetAuthStart> {
    const r = await apiClient.get<TeslaFleetAuthStart>('/tesla/fleet/auth/start');
    return r.data;
  },

  async syncHistory(): Promise<TeslaFleetSyncResult> {
    const r = await apiClient.post<TeslaFleetSyncResult>('/tesla/fleet/sync-history');
    return r.data;
  },

  async disconnect(): Promise<void> {
    await apiClient.delete('/tesla/disconnect');
  },
};

export default teslaFleetService;

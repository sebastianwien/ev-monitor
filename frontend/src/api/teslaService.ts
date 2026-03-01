import apiClient from './axios';

export interface TeslaConnectionStatus {
  connected: boolean;
  vehicleName: string | null;
  lastSyncAt: string | null;
  autoImportEnabled: boolean;
}

export interface TeslaSyncResult {
  logsImported: number;
  vehicleName: string;
  batteryLevel: number;
}

export interface ConnectRequest {
  accessToken: string;
  vehicleId: string;
  vehicleName: string;
}

export interface ConnectResponse {
  success: boolean;
  message: string;
  vehicleName: string | null;
}

const teslaService = {
  /**
   * Get Tesla connection status
   */
  async getStatus(): Promise<TeslaConnectionStatus> {
    const response = await apiClient.get<TeslaConnectionStatus>('/tesla/status');
    return response.data;
  },

  /**
   * Connect Tesla account
   */
  async connect(request: ConnectRequest): Promise<ConnectResponse> {
    const response = await apiClient.post<ConnectResponse>('/tesla/connect', request);
    return response.data;
  },

  /**
   * Manual sync - fetch latest charging data
   */
  async sync(): Promise<TeslaSyncResult> {
    const response = await apiClient.post<TeslaSyncResult>('/tesla/sync');
    return response.data;
  },

  /**
   * Disconnect Tesla account
   */
  async disconnect(): Promise<void> {
    await apiClient.delete('/tesla/disconnect');
  },
};

export default teslaService;

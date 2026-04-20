import axiosInstance from './axios';

export interface SpritMonitorVehicle {
  id: number;
  make: string;
  model: string;
  mainTankType: number;
  mainTank: number;
}

export interface ImportResult {
  imported: number;
  skipped: number;
  coinsAwarded: number;
  withoutLocation: number;
  errors: string[];
}

export const spritMonitorService = {
  /**
   * Fetches electric vehicles from Sprit-Monitor
   * @param token Sprit-Monitor API token (NOT persisted!)
   */
  async fetchVehicles(token: string): Promise<SpritMonitorVehicle[]> {
    const response = await axiosInstance.post('/import/sprit-monitor/vehicles', {
      token,
    });
    return response.data;
  },

  /**
   * Imports fuelings from Sprit-Monitor for a specific vehicle
   * @param token Sprit-Monitor API token (NOT persisted!)
   * @param vehicleId Sprit-Monitor vehicle ID
   * @param carId EV Monitor car ID
   */
  async importFuelings(
    token: string,
    vehicleId: number,
    mainTankId: number,
    carId: string
  ): Promise<ImportResult> {
    const response = await axiosInstance.post('/import/sprit-monitor/fuelings', {
      token,
      vehicleId,
      mainTankId,
      carId,
    });
    return response.data;
  },

  /**
   * Deletes all Sprit-Monitor imports for the authenticated user.
   * Only deletes entries with data_source = SPRITMONITOR_IMPORT.
   * Does NOT delete USER_LOGGED entries or other import sources.
   */
  async deleteAllImports(): Promise<void> {
    await axiosInstance.delete('/import/sprit-monitor/delete-all');
  },

  /**
   * Re-fetches fuelings from SpritMonitor and updates raw_import_data only.
   * No ev_log fields (kwhCharged, odometer, etc.) are changed.
   * Guard: skips entries with no match or more than one match (ambiguous).
   */
  async refreshRawImportData(
    token: string,
    vehicleId: number,
    mainTankId: number,
    carId: string
  ): Promise<RefreshRawResult> {
    const response = await axiosInstance.post('/import/sprit-monitor/refresh-raw', {
      token,
      vehicleId,
      mainTankId,
      carId,
    });
    return response.data;
  },
};

export interface RefreshRawResult {
  refreshed: number;
  skipped: number;
  errors: string[];
}

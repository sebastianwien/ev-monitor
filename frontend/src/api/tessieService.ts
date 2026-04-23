import axiosInstance from './axios';

export interface TessieVehicle {
  vin: string;
  displayName: string;
  isActive: boolean;
}

export interface TessieImportResult {
  drivesImported: number;
  chargesImported: number;
  skipped: number;
}

export const tessieService = {
  async fetchVehicles(token: string): Promise<TessieVehicle[]> {
    const response = await axiosInstance.post('/import/tessie/vehicles', { token });
    return response.data;
  },

  async importVin(token: string, vin: string): Promise<TessieImportResult> {
    const response = await axiosInstance.post('/import/tessie/import', { token, vin });
    return response.data;
  },
};

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
  evLogsCreated: number;
  evTripsCreated: number;
}

export const tessieService = {
  async fetchVehicles(token: string): Promise<TessieVehicle[]> {
    const response = await axiosInstance.post('/import/tessie/vehicles', { token });
    return response.data;
  },

  async importVin(token: string, vin: string, carId: string): Promise<TessieImportResult> {
    const response = await axiosInstance.post('/import/tessie/import', { token, vin, carId });
    return response.data;
  },
};

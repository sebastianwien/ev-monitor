import axiosInstance from './axios';

export interface ManualImportResult {
  imported: number;
  skipped: number;
  errors: number;
  warnings?: number;
}

export const manualImportService = {
  async importData(carId: string, format: 'csv' | 'json', data: string, mergeSessions: boolean): Promise<ManualImportResult> {
    const response = await axiosInstance.post('/import/sessions', { carId, format, data, mergeSessions });
    return response.data;
  },
  async importTrips(carId: string, format: 'csv' | 'json', data: string): Promise<ManualImportResult> {
    const response = await axiosInstance.post('/import/trips', { carId, format, data });
    return response.data;
  },
};

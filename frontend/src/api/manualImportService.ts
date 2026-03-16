import axiosInstance from './axios';

export interface ManualImportResult {
  imported: number;
  skipped: number;
  errors: number;
}

export const manualImportService = {
  async importData(carId: string, format: 'csv' | 'json', data: string, mergeSessions: boolean): Promise<ManualImportResult> {
    const response = await axiosInstance.post('/import/sessions', { carId, format, data, mergeSessions });
    return response.data;
  },
};

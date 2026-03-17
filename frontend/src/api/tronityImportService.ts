import axiosInstance from './axios';
import type { ManualImportResult } from './manualImportService';

export const tronityImportService = {
  async importData(carId: string, data: string, mergeSessions: boolean): Promise<ManualImportResult> {
    const response = await axiosInstance.post('/import/tronity', { carId, data, mergeSessions });
    return response.data;
  },
};

import axiosInstance from './axios';

export interface TeslaLoggerImportResult {
  imported: number;
  skipped: number;
  coinsAwarded: number;
  errors: string[];
}

export const teslaLoggerService = {
  /**
   * Imports charging sessions from a TeslaMate / TeslaLogger / TeslaFi export.
   * @param carId  EV Monitor car ID
   * @param format 'csv' or 'json'
   * @param data   Raw file content as string
   */
  async importData(carId: string, format: 'csv' | 'json', data: string): Promise<TeslaLoggerImportResult> {
    const response = await axiosInstance.post('/import/tesla-logger', { carId, format, data });
    return response.data;
  },
};

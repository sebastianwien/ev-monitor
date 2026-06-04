import axiosInstance from './axios';

export interface EUDataActSessionPreview {
  startedAt: string;
  endedAt: string;
  durationMin: number;
  socBefore: number | null;
  socAfter: number | null;
  chargeType: 'AC' | 'DC' | null;
  maxChargingPowerKw: number | null;
  calculatedKwh: number | null;
  odometerKm: number | null;
  temperatureCelsius: number | null;
}

export interface EUDataActPreviewResult {
  vin: string;
  sessions: EUDataActSessionPreview[];
}

export interface EUDataActImportResult {
  imported: number;
  skipped: number;
  errors: number;
}

export const euDataActService = {
  async preview(file: File): Promise<EUDataActPreviewResult> {
    const form = new FormData();
    form.append('file', file);
    const response = await axiosInstance.post('/import/eu-data-act/preview', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  async importData(file: File, carId: string): Promise<EUDataActImportResult> {
    const form = new FormData();
    form.append('file', file);
    const response = await axiosInstance.post(`/import/eu-data-act/import?carId=${carId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};

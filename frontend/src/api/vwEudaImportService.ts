import axiosInstance from './axios';

export interface VwEudaSessionPreview {
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

export interface VwEudaPreviewResult {
  vin: string;
  sessions: VwEudaSessionPreview[];
}

export interface VwEudaImportResult {
  imported: number;
  skipped: number;
  errors: number;
}

export const vwEudaImportService = {
  // carId ist noetig, weil die Batteriekapazitaet in die kWh-Berechnung eingeht:
  // die MEB-Variante des Exports liefert keine Ladeleistung, nur den SoC-Verlauf.
  async preview(file: File, carId: string): Promise<VwEudaPreviewResult> {
    const form = new FormData();
    form.append('file', file);
    const response = await axiosInstance.post(`/import/eu-data-act/preview?carId=${carId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  async importData(file: File, carId: string): Promise<VwEudaImportResult> {
    const form = new FormData();
    form.append('file', file);
    const response = await axiosInstance.post(`/import/eu-data-act/import?carId=${carId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};

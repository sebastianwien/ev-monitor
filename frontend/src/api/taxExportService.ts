import api from './axios';

export interface TaxExportPreview {
    sessionCount: number;
    totalKwh: number;
    totalCostEur: number;
    tariffPerKwh: number;
    isPauschale: boolean;
}

export const taxExportService = {
    async getPreview(
        carId: string,
        from: string,
        to: string,
        usePauschale: boolean,
        customTariff?: number
    ): Promise<TaxExportPreview> {
        const params: Record<string, string> = { carId, from, to, usePauschale: String(usePauschale) };
        if (!usePauschale && customTariff != null) {
            params.customTariff = String(customTariff);
        }
        const response = await api.get('/tax-export/preview', { params });
        return response.data;
    },

    async downloadCsv(carId: string, from: string, to: string, usePauschale: boolean, customTariff?: number): Promise<Blob> {
        const params: Record<string, string> = { carId, from, to, usePauschale: String(usePauschale) };
        if (!usePauschale && customTariff != null) params.customTariff = String(customTariff);
        const response = await api.get('/tax-export/csv', { params, responseType: 'blob' });
        return response.data;
    },

    async downloadPdf(carId: string, from: string, to: string, usePauschale: boolean, customTariff?: number): Promise<Blob> {
        const params: Record<string, string> = { carId, from, to, usePauschale: String(usePauschale) };
        if (!usePauschale && customTariff != null) params.customTariff = String(customTariff);
        const response = await api.get('/tax-export/pdf', { params, responseType: 'blob' });
        return response.data;
    }
};

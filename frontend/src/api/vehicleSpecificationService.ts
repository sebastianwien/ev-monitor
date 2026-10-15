import api from './axios';

export interface VehicleSpecification {
    id: string;
    carBrand: string;
    carModel: string;
    batteryCapacityKwh: number;
    netBatteryCapacityKwh: number | null;
    officialRangeKm: number;
    officialConsumptionKwhPer100km: number;
    wltpType: string;
    ratingSource: string;
    variantName: string | null;
    availableFrom: string | null;
    availableTo: string | null;
    createdAt: string;
    updatedAt: string;
}

/** Nur Lesezugriff: Zeilen entstehen im Admin-Tab, Lücken meldet der Nutzer über vehicleRequestService. */
export const vehicleSpecificationService = {
    /**
     * Lookup WLTP data for a specific vehicle configuration.
     * Returns null if no data exists (404).
     */
    async lookup(brand: string, model: string, capacityKwh: number, ratingSource: 'WLTP' | 'EPA' = 'WLTP'): Promise<VehicleSpecification | null> {
        try {
            const response = await api.get('/vehicle-specifications/lookup', {
                params: {
                    brand,
                    model,
                    capacityKwh,
                    ratingSource,
                }
            });
            return response.data;
        } catch (error: any) {
            if (error.response?.status === 404) {
                return null;
            }
            throw error;
        }
    },

    async lookupById(id: string): Promise<VehicleSpecification | null> {
        try {
            const response = await api.get(`/vehicle-specifications/${id}`);
            return response.data;
        } catch (error: any) {
            if (error.response?.status === 404) return null;
            throw error;
        }
    },
};

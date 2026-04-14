import api from './axios';

export interface VehicleSpecification {
    id: string;
    carBrand: string;
    carModel: string;
    batteryCapacityKwh: number;
    officialRangeKm: number;
    officialConsumptionKwhPer100km: number;
    wltpType: string;
    variantName: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface VehicleSpecificationRequest {
    carBrand: string;
    carModel: string;
    batteryCapacityKwh: number;
    officialRangeKm: number;
    officialConsumptionKwhPer100km: number;
    ratingSource?: 'WLTP' | 'EPA';
}

export interface VehicleSpecificationCreateResponse {
    specification: VehicleSpecification;
    coinsAwarded: number;
}

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

    /**
     * Create new WLTP data entry and earn coins.
     */
    async create(data: VehicleSpecificationRequest): Promise<VehicleSpecificationCreateResponse> {
        const response = await api.post('/vehicle-specifications', data);
        return response.data;
    }
};

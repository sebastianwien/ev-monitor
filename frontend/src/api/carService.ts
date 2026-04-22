import api from './axios';

export interface Car {
    id: string;
    userId: string;
    brand: string; // CarBrand enum name (e.g., "TESLA")
    model: string; // CarModel enum name (e.g., "MODEL_3")
    year: number;
    licensePlate: string;
    trim: string | null; // Trim level (e.g., "GTX", "Pro Performance")
    batteryCapacityKwh: number; // Selected capacity
    powerKw: number | null; // Power in kW (optional)
    registrationDate: string; // ISO date (YYYY-MM-DD)
    deregistrationDate: string | null; // ISO date or null if still active
    status: 'ACTIVE' | 'INACTIVE';
    createdAt: string;
    updatedAt: string;
    imageUrl: string | null; // Relative URL to image, e.g. /api/cars/{id}/image
    imagePublic: boolean;
    isPrimary: boolean;
    batteryDegradationPercent: number | null;
    effectiveBatteryCapacityKwh: number | null;
    isBusinessCar: boolean;
    hasHeatPump: boolean;
    vehicleSpecificationId: string | null;
}

export interface CarRequest {
    model: string; // CarModel enum name
    year: number;
    licensePlate: string;
    trim: string | null; // Trim level (optional)
    batteryCapacityKwh: number; // Selected or custom capacity
    powerKw: number | null; // Power in kW (optional)
    batteryDegradationPercent: number | null;
    hasHeatPump: boolean;
    vehicleSpecificationId: string | null;
}

export interface BrandInfo {
    value: string; // CarBrand enum name
    label: string; // Display name
}

export interface CapacityOption {
    kWh: number;
    variantName: string | null;
    vehicleSpecificationId: string | null;
    trimLevel: string | null;
    availableFrom: string | null;
    availableTo: string | null;
}

export interface ModelInfo {
    value: string; // CarModel enum name (e.g., "MODEL_3")
    label: string; // Display name (e.g., "Model 3")
    capacities: CapacityOption[]; // Available battery capacities with optional variant names
}

export interface CarCreateResponse {
    car: Car;
    coinsAwarded: number;
}

export interface CarImageResponse {
    car: Car;
    coinsAwarded: number;
}

export interface BatterySohEntry {
    id: string;
    carId: string;
    sohPercent: number;
    recordedAt: string; // ISO date
    createdAt: string;
}

export interface BatterySohRequest {
    sohPercent: number;
    recordedAt: string; // ISO date
}

export const carService = {
    async getCars(): Promise<Car[]> {
        const response = await api.get('/cars');
        return Array.isArray(response.data) ? response.data : [];
    },

    async getCarById(id: string): Promise<Car> {
        const response = await api.get(`/cars/${id}`);
        return response.data;
    },

    async createCar(carData: CarRequest): Promise<CarCreateResponse> {
        const response = await api.post('/cars', carData);
        return response.data;
    },

    async updateCar(id: string, carData: CarRequest): Promise<Car> {
        const response = await api.put(`/cars/${id}`, carData);
        return response.data;
    },

    async deleteCar(id: string): Promise<void> {
        await api.delete(`/cars/${id}`);
    },

    async getBrands(): Promise<BrandInfo[]> {
        const response = await api.get('/cars/brands');
        return response.data;
    },

    async getModelsForBrand(brand: string): Promise<ModelInfo[]> {
        const response = await api.get(`/cars/brands/${brand}/models`);
        return response.data;
    },

    async updateCarImageVisibility(carId: string, isPublic: boolean): Promise<CarImageResponse> {
        const response = await api.patch(`/cars/${carId}/image?isPublic=${isPublic}`);
        return response.data;
    },

    async uploadCarImage(carId: string, file: File, isPublic: boolean): Promise<CarImageResponse> {
        const formData = new FormData();
        formData.append('file', file);
        const response = await api.post(`/cars/${carId}/image?isPublic=${isPublic}`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return response.data;
    },

    async deleteCarImage(carId: string): Promise<void> {
        await api.delete(`/cars/${carId}/image`);
    },

    async getCarImageBlobUrl(carId: string): Promise<string> {
        const response = await api.get(`/cars/${carId}/image`, { responseType: 'blob' });
        return URL.createObjectURL(response.data);
    },

    async setActiveCar(carId: string): Promise<Car> {
        const response = await api.put(`/cars/${carId}/activate`);
        return response.data;
    },

    async setBusinessCar(carId: string, isBusinessCar: boolean): Promise<Car> {
        const response = await api.patch(`/cars/${carId}/business-car?isBusinessCar=${isBusinessCar}`);
        return response.data;
    },

    // SoH History
    async getSohHistory(carId: string): Promise<BatterySohEntry[]> {
        const response = await api.get(`/cars/${carId}/soh`);
        return response.data;
    },

    async addSohMeasurement(carId: string, data: BatterySohRequest): Promise<BatterySohEntry> {
        const response = await api.post(`/cars/${carId}/soh`, data);
        return response.data;
    },

    async updateSohMeasurement(carId: string, entryId: string, data: BatterySohRequest): Promise<BatterySohEntry> {
        const response = await api.put(`/cars/${carId}/soh/${entryId}`, data);
        return response.data;
    },

    async deleteSohMeasurement(carId: string, entryId: string): Promise<void> {
        await api.delete(`/cars/${carId}/soh/${entryId}`);
    }
};

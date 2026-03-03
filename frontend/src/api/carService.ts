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
    availableCapacities: number[]; // Available capacities from enum
    registrationDate: string; // ISO date (YYYY-MM-DD)
    deregistrationDate: string | null; // ISO date or null if still active
    status: 'ACTIVE' | 'INACTIVE';
    createdAt: string;
    updatedAt: string;
    imageUrl: string | null; // Relative URL to image, e.g. /api/cars/{id}/image
    imagePublic: boolean;
}

export interface CarRequest {
    model: string; // CarModel enum name
    year: number;
    licensePlate: string;
    trim: string | null; // Trim level (optional)
    batteryCapacityKwh: number; // Selected or custom capacity
    powerKw: number | null; // Power in kW (optional)
}

export interface BrandInfo {
    value: string; // CarBrand enum name
    label: string; // Display name
}

export interface ModelInfo {
    value: string; // CarModel enum name (e.g., "MODEL_3")
    label: string; // Display name (e.g., "Model 3")
    capacities: number[]; // Available battery capacities
}

export interface CarCreateResponse {
    car: Car;
    coinsAwarded: number;
}

export const carService = {
    async getCars(): Promise<Car[]> {
        const response = await api.get('/cars');
        return response.data;
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

    async uploadCarImage(carId: string, file: File, isPublic: boolean): Promise<Car> {
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
    }
};

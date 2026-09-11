import { defineStore } from 'pinia'
import { ref } from 'vue'
import { carService, type Car, type BrandInfo, type ModelInfo } from '../api/carService'

export const useCarStore = defineStore('cars', () => {
    const cars = ref<Car[]>([])
    const brands = ref<BrandInfo[]>([])
    const modelsByBrand = ref<Map<string, ModelInfo[]>>(new Map())

    let carsPromise: Promise<Car[]> | null = null
    let brandsPromise: Promise<BrandInfo[]> | null = null
    const modelsPromises = new Map<string, Promise<ModelInfo[]>>()
    const carsLoaded = ref(false)
    let brandsLoaded = false

    async function getCars(forceRefresh = false): Promise<Car[]> {
        if (!forceRefresh && carsLoaded.value) return cars.value
        if (!carsPromise) {
            carsPromise = carService.getCars().then(data => {
                cars.value = data
                carsLoaded.value = true
                carsPromise = null
                return data
            }).catch(err => {
                carsPromise = null
                throw err
            })
        }
        return carsPromise
    }

    async function getBrands(): Promise<BrandInfo[]> {
        if (brandsLoaded) return brands.value
        if (!brandsPromise) {
            brandsPromise = carService.getBrands().then(data => {
                brands.value = data
                brandsLoaded = true
                brandsPromise = null
                return data
            }).catch(err => {
                brandsPromise = null
                throw err
            })
        }
        return brandsPromise
    }

    async function getModelsForBrand(brand: string): Promise<ModelInfo[]> {
        if (modelsByBrand.value.has(brand)) return modelsByBrand.value.get(brand)!
        if (modelsPromises.has(brand)) return modelsPromises.get(brand)!

        const promise = carService.getModelsForBrand(brand).then(data => {
            modelsByBrand.value = new Map(modelsByBrand.value).set(brand, data)
            modelsPromises.delete(brand)
            return data
        }).catch(err => {
            modelsPromises.delete(brand)
            throw err
        })
        modelsPromises.set(brand, promise)
        return promise
    }

    function invalidateCars() {
        carsLoaded.value = false
        carsPromise = null
    }

    function reset() {
        cars.value = []
        brands.value = []
        modelsByBrand.value = new Map()
        carsLoaded.value = false
        brandsLoaded = false
        carsPromise = null
        brandsPromise = null
        modelsPromises.clear()
    }

    return { cars, brands, carsLoaded, getCars, getBrands, getModelsForBrand, invalidateCars, reset }
})

import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { carService, type Car, type CarRequest, type BrandInfo, type ModelInfo, type CarCreateResponse, type BatterySohEntry, type CapacityOption } from '../api/carService'
import { useCarStore } from '../stores/car'
import { useCoinStore } from '../stores/coins'
import { analytics } from '../services/analytics'
import { useTeslaStatus } from './useTeslaStatus'

export function resolveCapacityForCar(
  car: Car,
  capacities: CapacityOption[]
): { selectedCapacity: number | null; useCustom: boolean; customCapacity: number | null; kwhCorrected: boolean } {
  if (car.vehicleSpecificationId) {
    const specMatch = capacities.find(c => c.vehicleSpecificationId === car.vehicleSpecificationId)
    if (specMatch) return {
      selectedCapacity: specMatch.kWh,
      useCustom: false,
      customCapacity: null,
      kwhCorrected: specMatch.kWh !== car.batteryCapacityKwh,
    }
  }
  if (capacities.some(c => c.kWh === car.batteryCapacityKwh)) {
    return { selectedCapacity: car.batteryCapacityKwh, useCustom: false, customCapacity: null, kwhCorrected: false }
  }
  return { selectedCapacity: null, useCustom: true, customCapacity: car.batteryCapacityKwh, kwhCorrected: false }
}

export function useCarForm() {
  const { t } = useI18n()
  const carStore = useCarStore()
  const coinStore = useCoinStore()
  const { teslaStatus, start: startTeslaPolling } = useTeslaStatus()

  const cars = ref<Car[]>([])
  const brands = ref<BrandInfo[]>([])
  const availableModels = ref<ModelInfo[]>([])
  const loading = ref(true)
  const error = ref<string | null>(null)
  const showForm = ref(false)
  const editingCar = ref<Car | null>(null)
  const showToast = ref(false)
  const toastMessage = ref('')

  // Form fields
  const selectedBrand = ref('')
  const selectedModel = ref('')
  const year = ref<number>(new Date().getFullYear())
  const licensePlate = ref('')
  const trim = ref('')
  const selectedCapacity = ref<number | null>(null)
  const customCapacity = ref<number | null>(null)
  const useCustomCapacity = ref(false)
  const powerKw = ref<number | null>(null)
  const batteryDegradationPercent = ref<number | null>(null)
  const hasHeatPump = ref(false)
  const isBusinessCar = ref(false)
  const capacityWasCorrected = ref(false)

  // SoH History (kept here because resetForm touches it)
  const sohHistory = ref<BatterySohEntry[]>([])
  const showSohAddForm = ref(false)
  const sohEditingEntry = ref<BatterySohEntry | null>(null)
  const sohPercent = ref<number | null>(null)
  const sohDate = ref(new Date().toISOString().split('T')[0])

  const sortedBrands = computed(() => [...brands.value].sort((a, b) => a.label.localeCompare(b.label)))
  const isSonstige = computed(() => selectedBrand.value === 'SONSTIGE')

  const selectedModelCapacities = computed(() => {
    if (!selectedModel.value) return []
    const model = availableModels.value.find(m => m.value === selectedModel.value)
    return model?.capacities || []
  })

  const finalCapacity = computed(() => {
    if (useCustomCapacity.value) return customCapacity.value
    return selectedCapacity.value
  })

  // vehicleSpecificationId der gewählten Kapazität - null bei Custom-Eingabe oder ohne Spec
  const finalVehicleSpecificationId = computed<string | null>(() => {
    if (useCustomCapacity.value || !selectedCapacity.value) return null
    const cap = selectedModelCapacities.value.find(c => c.kWh === selectedCapacity.value)
    return cap?.vehicleSpecificationId ?? null
  })

  const powerPs = computed(() => {
    if (!powerKw.value) return null
    return Math.round(powerKw.value * 1.35962)
  })

  const fetchCars = async (loadImages: (carList: Car[]) => Promise<void>, revokeAll: () => void) => {
    try {
      loading.value = true
      error.value = null
      revokeAll()
      cars.value = await carStore.getCars(true) ?? []
      await loadImages(cars.value)
      await startTeslaPolling(cars.value.some((c: any) => c.brand?.toLowerCase() === 'tesla'))
      await new Promise(resolve => setTimeout(resolve, 150))
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_load')
    } finally {
      loading.value = false
    }
  }

  const fetchBrands = async () => {
    try {
      brands.value = await carStore.getBrands()
    } catch {
      error.value = t('cars.error_brands')
    }
  }

  const loadModelsForBrand = async (brand: string) => {
    if (!brand) { availableModels.value = []; return }
    try {
      availableModels.value = await carStore.getModelsForBrand(brand)
    } catch {
      error.value = t('cars.error_models')
    }
  }

  let suppressNextBrandWatch = false

  watch(selectedBrand, (newBrand) => {
    if (suppressNextBrandWatch) { suppressNextBrandWatch = false; return }
    if (newBrand) { loadModelsForBrand(newBrand) } else { availableModels.value = [] }
    if (!editingCar.value) {
      selectedCapacity.value = null
      if (newBrand === 'SONSTIGE') {
        selectedModel.value = 'SONSTIGE_CUSTOM'
        useCustomCapacity.value = true
      } else {
        selectedModel.value = ''
        useCustomCapacity.value = false
      }
    }
  })

  watch(selectedModel, (newModel) => {
    if (!editingCar.value) {
      selectedCapacity.value = null
      if (newModel === 'SONSTIGE_CUSTOM') {
        useCustomCapacity.value = true
      } else {
        useCustomCapacity.value = false
        customCapacity.value = null
      }
    }
  })

  const resetForm = () => {
    selectedBrand.value = ''
    selectedModel.value = ''
    year.value = new Date().getFullYear()
    licensePlate.value = ''
    trim.value = ''
    selectedCapacity.value = null
    customCapacity.value = null
    useCustomCapacity.value = false
    powerKw.value = null
    batteryDegradationPercent.value = null
    hasHeatPump.value = false
    isBusinessCar.value = false
    capacityWasCorrected.value = false
    editingCar.value = null
    showForm.value = false
    availableModels.value = []
    sohHistory.value = []
    showSohAddForm.value = false
    sohEditingEntry.value = null
    sohPercent.value = null
    sohDate.value = new Date().toISOString().split('T')[0]
  }

  const openAddForm = () => {
    resetForm()
    showForm.value = true
  }

  const openEditForm = async (car: Car) => {
    editingCar.value = car
    suppressNextBrandWatch = true
    selectedBrand.value = car.brand
    await loadModelsForBrand(car.brand)
    selectedModel.value = car.model

    const foundModel = availableModels.value.find(m => m.value === car.model)
    const resolved = resolveCapacityForCar(car, foundModel?.capacities ?? [])
    selectedCapacity.value = resolved.selectedCapacity
    useCustomCapacity.value = resolved.useCustom
    customCapacity.value = resolved.customCapacity
    capacityWasCorrected.value = resolved.kwhCorrected

    year.value = car.year
    licensePlate.value = car.licensePlate
    trim.value = car.trim || ''
    powerKw.value = car.powerKw
    batteryDegradationPercent.value = car.batteryDegradationPercent
    hasHeatPump.value = car.hasHeatPump ?? false
    isBusinessCar.value = car.isBusinessCar ?? false
    showForm.value = true
    try {
      sohHistory.value = await carService.getSohHistory(car.id)
    } catch {
      sohHistory.value = []
    }
  }

  const submitForm = async (onCarsChanged: () => Promise<void>) => {
    try {
      error.value = null
      if (!finalCapacity.value) { error.value = t('cars.error_capacity'); return }

      const carData: CarRequest = {
        model: selectedModel.value,
        year: year.value,
        licensePlate: licensePlate.value,
        trim: trim.value || null,
        batteryCapacityKwh: finalCapacity.value,
        powerKw: powerKw.value,
        batteryDegradationPercent: batteryDegradationPercent.value,
        hasHeatPump: hasHeatPump.value,
        vehicleSpecificationId: finalVehicleSpecificationId.value
      }

      if (editingCar.value) {
        await carService.updateCar(editingCar.value.id, carData)
        if (isBusinessCar.value !== editingCar.value.isBusinessCar) {
          await carService.setBusinessCar(editingCar.value.id, isBusinessCar.value)
        }
        resetForm()
        await onCarsChanged()
      } else {
        const result: CarCreateResponse = await carService.createCar(carData)
        resetForm()
        await onCarsChanged()
        coinStore.refresh()
        const isFirst = result.coinsAwarded === 20
        analytics.trackCarAdded(isFirst)
        toastMessage.value = isFirst
          ? t('cars.toast_first', { n: result.coinsAwarded })
          : t('cars.toast_coins', { n: result.coinsAwarded })
        showToast.value = true
        setTimeout(() => { showToast.value = false }, 5000)
      }
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_save')
    }
  }

  const deleteCar = async (id: string, onCarsChanged: () => Promise<void>) => {
    if (!confirm(t('cars.confirm_delete'))) return
    try {
      error.value = null
      await carService.deleteCar(id)
      await onCarsChanged()
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_delete')
    }
  }

  const setActiveCar = async (id: string) => {
    try {
      error.value = null
      const updatedCar = await carService.setActiveCar(id)
      cars.value = cars.value.map(c => ({ ...c, isPrimary: c.id === updatedCar.id }))
      carStore.invalidateCars()
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_activate')
    }
  }

  const getModelLabel = (modelValue: string | null | undefined): string => {
    if (!modelValue) return ''
    const model = availableModels.value.find(m => m.value === modelValue)
    if (model) return model.label
    return modelValue.replace(/_/g, ' ').toLowerCase()
      .split(' ').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
  }

  return {
    cars, brands, availableModels, loading, error, showForm, editingCar,
    showToast, toastMessage, teslaStatus,
    // Form fields
    selectedBrand, selectedModel, year, licensePlate, trim,
    selectedCapacity, customCapacity, useCustomCapacity,
    powerKw, batteryDegradationPercent, hasHeatPump, isBusinessCar,
    // SoH (form state only - CRUD in useSohHistory)
    sohHistory, showSohAddForm, sohEditingEntry, sohPercent, sohDate,
    // Computed / flags
    sortedBrands, isSonstige, selectedModelCapacities, finalCapacity, finalVehicleSpecificationId, powerPs,
    capacityWasCorrected,
    // Actions
    fetchCars, fetchBrands, loadModelsForBrand, resetForm,
    openAddForm, openEditForm, submitForm, deleteCar, setActiveCar, getModelLabel,
  }
}

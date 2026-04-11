import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import type { Ref, ComputedRef } from 'vue'

export function useWltpLookup(
  selectedBrand: Ref<string>,
  selectedModel: Ref<string>,
  finalCapacity: ComputedRef<number | null>,
  editingCar: Ref<any>,
  error: Ref<string | null>,
  showToast: Ref<boolean>,
  toastMessage: Ref<string>,
) {
  const { t } = useI18n()

  const wltpData = ref<VehicleSpecification | null>(null)
  const showWltpQuestion = ref(false)
  const showWltpForm = ref(false)
  const wltpRangeKm = ref<number | null>(null)
  const wltpConsumptionKwhPer100km = ref<number | null>(null)

  const lookupWltpData = async () => {
    if (!selectedBrand.value || !selectedModel.value || !finalCapacity.value) return
    try {
      const data = await vehicleSpecificationService.lookup(
        selectedBrand.value, selectedModel.value, finalCapacity.value
      )
      if (data) { wltpData.value = data } else { showWltpQuestion.value = true }
    } catch {
      // Lookup failed silently
    }
  }

  const closeWltpQuestion = () => { showWltpQuestion.value = false }

  const openWltpForm = () => {
    showWltpQuestion.value = false
    showWltpForm.value = true
    wltpRangeKm.value = null
    wltpConsumptionKwhPer100km.value = null
  }

  const closeWltpForm = () => {
    showWltpForm.value = false
    wltpRangeKm.value = null
    wltpConsumptionKwhPer100km.value = null
  }

  const submitWltpData = async () => {
    if (!wltpRangeKm.value || !wltpConsumptionKwhPer100km.value) {
      error.value = t('cars.error_wltp')
      return
    }
    try {
      error.value = null
      const response = await vehicleSpecificationService.create({
        carBrand: selectedBrand.value,
        carModel: selectedModel.value,
        batteryCapacityKwh: finalCapacity.value!,
        wltpRangeKm: wltpRangeKm.value,
        wltpConsumptionKwhPer100km: wltpConsumptionKwhPer100km.value
      })
      closeWltpForm()
      toastMessage.value = t('cars.toast_wltp', { n: response.coinsAwarded })
      showToast.value = true
      setTimeout(() => { showToast.value = false }, 5000)
      wltpData.value = response.specification
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_wltp_save')
    }
  }

  // Reset WLTP data when model changes
  watch(selectedModel, () => { wltpData.value = null })

  // Lookup on brand/model/capacity change
  watch([selectedBrand, selectedModel, finalCapacity], async () => {
    wltpData.value = null
    showWltpQuestion.value = false
    if (!selectedBrand.value || !selectedModel.value || !finalCapacity.value || editingCar.value) return
    await lookupWltpData()
  })

  return {
    wltpData, showWltpQuestion, showWltpForm,
    wltpRangeKm, wltpConsumptionKwhPer100km,
    lookupWltpData, closeWltpQuestion, openWltpForm, closeWltpForm, submitWltpData,
  }
}

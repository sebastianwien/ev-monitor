import { ref, computed } from 'vue'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import { useCountryStore } from '../stores/country'
import type { Ref } from 'vue'

export function useWltpLookup(
  selectedBrand: Ref<string>,
  selectedModel: Ref<string>,
  selectedSpecId: Ref<string | null>,
  useCustomCapacity: Ref<boolean>,
) {
  const countryStore = useCountryStore()

  const wltpData = ref<VehicleSpecification | null>(null)
  const customNetCapacityKwh = ref<number | null>(null)
  const customGrossCapacityKwh = ref<number | null>(null)
  const officialRangeKm = ref<number | null>(null)
  const officialConsumptionKwhPer100km = ref<number | null>(null)

  const ratingSource = computed<'WLTP' | 'EPA'>(() =>
    countryStore.country === 'US' ? 'EPA' : 'WLTP'
  )

  const resetCustomFields = () => {
    customNetCapacityKwh.value = null
    customGrossCapacityKwh.value = null
    officialRangeKm.value = null
    officialConsumptionKwhPer100km.value = null
    wltpData.value = null
  }

  /**
   * Wird von submitForm aufgerufen wenn useCustomCapacity aktiv.
   * Legt eine neue Spec an und verknuepft sie mit dem Car.
   * Gibt true zurueck wenn erfolgreich, false bei Fehler.
   */
  const submitCustomSpec = async (): Promise<{ ok: boolean; error?: string; coinsAwarded?: number }> => {
    if (!customNetCapacityKwh.value || !customGrossCapacityKwh.value || !officialRangeKm.value || !officialConsumptionKwhPer100km.value) {
      return { ok: false, error: 'cars.error_capacity' }
    }
    try {
      const response = await vehicleSpecificationService.create({
        carBrand: selectedBrand.value,
        carModel: selectedModel.value,
        batteryCapacityKwh: customGrossCapacityKwh.value,
        netBatteryCapacityKwh: customNetCapacityKwh.value,
        officialRangeKm: officialRangeKm.value,
        officialConsumptionKwhPer100km: officialConsumptionKwhPer100km.value,
        ratingSource: ratingSource.value,
      })
      wltpData.value = response.specification
      selectedSpecId.value = response.specification.id
      useCustomCapacity.value = false
      return { ok: true, coinsAwarded: response.coinsAwarded }
    } catch (err: any) {
      return { ok: false, error: err.response?.data?.message ?? 'cars.error_wltp_save' }
    }
  }

  return {
    wltpData,
    customNetCapacityKwh, customGrossCapacityKwh,
    officialRangeKm, officialConsumptionKwhPer100km,
    ratingSource,
    resetCustomFields, submitCustomSpec,
  }
}

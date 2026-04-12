import { useI18n } from 'vue-i18n'
import { carService, type Car, type BatterySohEntry } from '../api/carService'
import { useCarStore } from '../stores/car'
import type { Ref } from 'vue'

export function useSohHistory(
  editingCar: Ref<Car | null>,
  cars: Ref<Car[]>,
  error: Ref<string | null>,
  sohHistory: Ref<BatterySohEntry[]>,
  showSohAddForm: Ref<boolean>,
  sohEditingEntry: Ref<BatterySohEntry | null>,
  sohPercent: Ref<number | null>,
  sohDate: Ref<string>,
) {
  const { t } = useI18n()
  const carStore = useCarStore()

  const openSohAddForm = () => {
    sohEditingEntry.value = null
    sohPercent.value = null
    sohDate.value = new Date().toISOString().split('T')[0]
    showSohAddForm.value = true
  }

  const openSohEditForm = (entry: BatterySohEntry) => {
    sohEditingEntry.value = entry
    sohPercent.value = entry.sohPercent
    sohDate.value = entry.recordedAt
    showSohAddForm.value = true
  }

  const cancelSohForm = () => {
    showSohAddForm.value = false
    sohEditingEntry.value = null
    sohPercent.value = null
    sohDate.value = new Date().toISOString().split('T')[0]
  }

  const submitSohForm = async () => {
    if (!editingCar.value || !sohPercent.value || !sohDate.value) return
    try {
      error.value = null
      if (sohEditingEntry.value) {
        const updated = await carService.updateSohMeasurement(editingCar.value.id, sohEditingEntry.value.id, {
          sohPercent: sohPercent.value,
          recordedAt: sohDate.value
        })
        sohHistory.value = sohHistory.value.map(e => e.id === updated.id ? updated : e)
      } else {
        const created = await carService.addSohMeasurement(editingCar.value.id, {
          sohPercent: sohPercent.value,
          recordedAt: sohDate.value
        })
        sohHistory.value = [created, ...sohHistory.value]
          .sort((a, b) => b.recordedAt.localeCompare(a.recordedAt))
        cars.value = cars.value.map(c => c.id === editingCar.value!.id
          ? { ...c, batteryDegradationPercent: 100 - sohPercent.value!, effectiveBatteryCapacityKwh: c.batteryCapacityKwh * sohPercent.value! / 100 }
          : c)
      }
      cancelSohForm()
      carStore.invalidateCars()
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.soh_error_save')
    }
  }

  const deleteSohEntry = async (entry: BatterySohEntry) => {
    if (!editingCar.value || !confirm(t('cars.soh_confirm_delete'))) return
    try {
      await carService.deleteSohMeasurement(editingCar.value.id, entry.id)
      sohHistory.value = sohHistory.value.filter(e => e.id !== entry.id)
      carStore.invalidateCars()
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.soh_error_delete')
    }
  }

  return {
    openSohAddForm, openSohEditForm, cancelSohForm, submitSohForm, deleteSohEntry,
  }
}

import { ref, watchEffect, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import axios from 'axios'

export interface LiveChargingData {
  isActive: boolean
  chargingType: string | null
  powerKw: number | null
  socPercent: number | null
  energyRemainingKwh: number | null
  timeToFullMinutes: number | null
  estRangeKm: number | null
  chargeAmps: number | null
  sessionStartedAt: string | null
  socAtSessionStart: number | null
  chargeLimitSoc: number | null
  lastUpdatedAt: string | null
}

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let intervalId: ReturnType<typeof setInterval> | undefined

  const fetch = async () => {
    if (!carId.value) return
    try {
      const res = await axios.get<LiveChargingData>(`/api/cars/${carId.value}/live/charging`)
      data.value = res.data
      error.value = null
    } catch {
      error.value = 'error'
    }
  }

  watchEffect(() => {
    clearInterval(intervalId)
    if (carId.value) {
      loading.value = true
      fetch().finally(() => { loading.value = false })
      intervalId = setInterval(fetch, 5000)
    }
  })

  onUnmounted(() => clearInterval(intervalId))

  return { data, loading, error, refresh: fetch }
}

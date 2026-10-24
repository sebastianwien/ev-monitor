import { ref, watch, type Ref, type WatchSource } from 'vue'
import api from '../api/axios'
import { carService } from '../api/carService'

/**
 * Einzelne Kennzahlen eines Autos aus dem Backend, ohne Cache: die Abfragen sind billig, und ein
 * Cache wüsste nichts von neuen Telemetrie-Fahrten. Geladen wird beim Autowechsel und wenn eine
 * der reloadOn-Quellen sich ändert, z. B. nach dem Speichern eines Logs. Beim Autowechsel wird
 * der alte Wert sofort verworfen, eine verspätete Antwort für ein früheres Auto ignoriert.
 */
function useCarValue<T>(carId: Readonly<Ref<string | null>>, fetch: (id: string) => Promise<T | null>,
  reloadOn: WatchSource[]): Ref<T | null> {
  const value = ref<T | null>(null) as Ref<T | null>
  let seq = 0
  let loadedFor: string | null = null

  async function load() {
    const id = carId.value
    const mySeq = ++seq
    if (id !== loadedFor) { value.value = null; loadedFor = id }
    if (!id) return
    try {
      const result = await fetch(id)
      if (mySeq === seq) value.value = result
    } catch {
      // Netzfehler: letzten Stand behalten
    }
  }

  watch([carId, ...reloadOn], load, { immediate: true })
  return value
}

/** Neuester Tachostand aus Ladevorgängen und Fahrten. */
export function useCarOdometer(carId: Readonly<Ref<string | null>>, reloadOn: WatchSource[] = []): Ref<number | null> {
  return useCarValue(carId, async id => (await carService.getOdometer(id)).currentOdometerKm, reloadOn)
}

/** Ø Verbrauch über die ganze Zeit, dieselbe Zahl wie das Dashboard (EvLogService im Backend). */
export function useCarAvgConsumption(carId: Readonly<Ref<string | null>>, reloadOn: WatchSource[] = []): Ref<number | null> {
  return useCarValue(carId, async id => {
    const res = await api.get('/logs/statistics', { params: { carId: id, timeRange: 'ALL_TIME', groupBy: 'MONTH' } })
    const avg = res.data?.avgConsumptionKwhPer100km
    return avg == null ? null : Number(avg)
  }, reloadOn)
}

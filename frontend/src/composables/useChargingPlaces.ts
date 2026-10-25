import { ref, computed } from 'vue'
import api from '../api/axios'
import { chargingBenchmarkService, type ChargingBenchmark } from '../api/chargingBenchmarkService'
import { placeClass, benchmarkKey, benchmarkPairs, sortPlaces, type ChargingPlace, type DayWindow, type PlaceRow } from './storyPlaces'

/**
 * Ladeorte eines Autos in einem Zeitraum samt Preisvergleich je Betreiber und Ladeart.
 * Geteilt von Sheet und Teaser der Ladekarte. Vergleiche merkt sich jede Instanz über Zeiträume hinweg.
 */
export function useChargingPlaces(carId: () => string | null) {
  const places = ref<ChargingPlace[]>([])
  const benchmarks = ref<Record<string, ChargingBenchmark | null>>({})
  const loading = ref(false)
  const error = ref(false)

  const rows = computed<PlaceRow[]>(() => sortPlaces(places.value).map(p => {
    const key = benchmarkKey(p)
    const benchmark = key ? benchmarks.value[key] ?? null : null
    return { place: p, cls: placeClass(p, benchmark), benchmark }
  }))

  let token = 0
  /** Orte, danach die fehlenden Vergleiche. Eine spätere Anfrage gewinnt gegen eine frühere. */
  async function load(range: DayWindow): Promise<void> {
    const id = carId()
    if (!id) return
    const mine = ++token
    loading.value = true
    error.value = false
    const params = { carId: id, ...(range.from ? { from: range.from } : {}), ...(range.to ? { to: range.to } : {}) }
    try {
      const res = await api.get<ChargingPlace[]>('/logs/places', { params })
      if (mine !== token) return
      places.value = Array.isArray(res.data) ? res.data : []
    } catch {
      if (mine !== token) return
      error.value = true
      return
    } finally {
      if (mine === token) loading.value = false
    }
    await loadBenchmarks()
  }

  /** Ein Aufruf je Betreiber und Ladeart, parallel. 204 (unter 5 Fahrern) = null. */
  async function loadBenchmarks() {
    const missing = benchmarkPairs(places.value).filter(p => !(`${p.cpo}|${p.type}` in benchmarks.value))
    if (!missing.length) return
    const results = await Promise.allSettled(missing.map(p => chargingBenchmarkService.get(p.cpo, p.type)))
    const next = { ...benchmarks.value }
    results.forEach((r, i) => { next[`${missing[i].cpo}|${missing[i].type}`] = r.status === 'fulfilled' ? r.value : null })
    benchmarks.value = next
  }

  return { places, rows, loading, error, load }
}

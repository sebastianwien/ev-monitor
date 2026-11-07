import { computed, ref, shallowRef, triggerRef } from 'vue'
import { chargingStationService, type MapStation, type StationCell, type StationMapResponse } from '../api/chargingStationService'

/** Felder gleichzeitig: genug, dass ein neuer Ausschnitt in rund einer Sekunde steht, ohne das Register zu fluten. */
const CONCURRENCY = 4

interface Api { cells(cells: string[], signal?: AbortSignal): Promise<StationMapResponse> }

/**
 * Säulen der Karte je Feld (Geohash 6). Jedes Feld kommt als eigene Anfrage: Felder aus dem
 * Server-Cache stehen sofort, neue (Register, rund 0,5 s) laden parallel nach, die vorhandenen
 * Pins bleiben dabei stehen. Ein Feld ohne Antwort (Drossel, Register aus) bleibt offen und kommt
 * mit `retry` erneut. Beim Verlassen bricht `dispose` alles ab.
 */
export function useStationsData(api: Api = chargingStationService) {
  const loaded = shallowRef(new Map<string, StationCell>())
  const missing = ref(new Set<string>())
  const inflight = ref(0)
  const queue: string[] = []
  const queued = new Set<string>()
  const controller = new AbortController()

  const stations = computed<MapStation[]>(() => {
    const byId = new Map<string, MapStation>()
    for (const cell of loaded.value.values()) for (const s of cell.stations) if (!byId.has(s.id)) byId.set(s.id, s)
    return [...byId.values()]
  })

  function pump() {
    while (inflight.value < CONCURRENCY && queue.length && !controller.signal.aborted) {
      const cell = queue.shift()!
      inflight.value++
      api.cells([cell], controller.signal)
        .then(res => {
          if (controller.signal.aborted) return
          for (const c of res.cells) loaded.value.set(c.cell, c)
          triggerRef(loaded)
          if (res.missing.includes(cell)) missing.value.add(cell)
          else missing.value.delete(cell)
        })
        .catch(() => { if (!controller.signal.aborted) missing.value.add(cell) })
        .finally(() => {
          queued.delete(cell)
          inflight.value--
          pump()
        })
    }
  }

  /** Felder laden, die weder da noch unterwegs sind, in der gegebenen Reihenfolge; offene bleiben bis `retry` liegen. */
  function request(cells: string[]) {
    for (const c of cells) {
      if (loaded.value.has(c) || queued.has(c) || missing.value.has(c)) continue
      queued.add(c)
      queue.push(c)
    }
    pump()
  }

  /** Offene Felder erneut fragen (Knopf "Erneut versuchen"). */
  function retry() {
    const again = [...missing.value]
    missing.value.clear()
    request(again)
  }

  function dispose() {
    controller.abort()
    queue.length = 0
  }

  return {
    stations,
    loading: computed(() => inflight.value > 0),
    failed: computed(() => missing.value.size > 0),
    isLoaded: (cell: string) => loaded.value.has(cell),
    cellInfo: (cell: string) => loaded.value.get(cell),
    request,
    retry,
    dispose,
  }
}

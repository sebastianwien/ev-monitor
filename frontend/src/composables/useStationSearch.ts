import { ref, watch } from 'vue'
import api from '../api/axios'
import type { StationMatch } from './useNearbyStations'

const MIN_CHARS = 3
const DEBOUNCE_MS = 300

/**
 * Textsuche im Ladesäulenregister über das eigene Backend: "EnBW Lichtenau" liefert die Säule
 * als Kachel. Entprellt ab drei Zeichen, Antworten überholter Anfragen werden verworfen.
 */
export function useStationSearch() {
  const query = ref('')
  const matches = ref<StationMatch[]>([])
  const loading = ref(false)
  /** Suche beantwortet, auch mit leerer Liste - Grundlage für Hinweise statt stummer Leere */
  const searched = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let requestNo = 0
  /** Übernommener Name einer gewählten Säule: löst keine erneute Suche aus */
  let settled: string | null = null

  watch(query, (q) => {
    if (timer) clearTimeout(timer)
    searched.value = false
    const text = q.trim()
    if (text === settled) return
    settled = null
    if (text.length < MIN_CHARS) { matches.value = []; loading.value = false; return }
    timer = setTimeout(async () => {
      const mine = ++requestNo
      loading.value = true
      try {
        const res = await api.get('/charging-provider-tariffs/cpos/search-stations', { params: { q: text } })
        if (mine !== requestNo) return
        matches.value = Array.isArray(res.data) ? res.data : []
      } catch {
        if (mine !== requestNo) return
        matches.value = []
      } finally {
        if (mine === requestNo) { loading.value = false; searched.value = true }
      }
    }, DEBOUNCE_MS)
  })

  const reset = () => {
    if (timer) clearTimeout(timer)
    requestNo++; matches.value = []; searched.value = false; loading.value = false
  }
  /** Wahl übernehmen: Name ins Feld, Liste zu, keine neue Suche */
  const select = (name: string) => { settled = name.trim(); query.value = name; reset() }

  return { query, matches, loading, searched, reset, select }
}

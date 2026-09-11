import { ref, watch } from 'vue'

export interface PlaceSuggestion { place_id: number; lat: string; lon: string; display_name: string }
export interface PickedPlace { latitude: number; longitude: number; name: string }

/**
 * Ortssuche über Nominatim (OpenStreetMap), entprellt ab drei Zeichen.
 * Für Ladungen ohne Live-Position: nachträglich erfasst oder auf einem anderen Gerät.
 * Die Koordinaten des gewählten Orts gehen nur an das eigene Backend, das sie in eine
 * Geohash-Zelle umrechnet.
 */
export function useLocationSearch() {
  const query = ref('')
  const suggestions = ref<PlaceSuggestion[]>([])
  const selectedName = ref('')
  let timer: ReturnType<typeof setTimeout> | null = null

  watch(query, (q) => {
    if (timer) clearTimeout(timer)
    if (!q || q.length < 3 || q === selectedName.value) { suggestions.value = []; return }
    timer = setTimeout(async () => {
      try {
        const res = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5`)
        const data = await res.json()
        suggestions.value = Array.isArray(data) ? data : []
      } catch {
        suggestions.value = []
      }
    }, 300)
  })

  const select = (s: PlaceSuggestion): PickedPlace => {
    selectedName.value = s.display_name
    query.value = s.display_name
    suggestions.value = []
    return { latitude: parseFloat(s.lat), longitude: parseFloat(s.lon), name: s.display_name }
  }

  return { query, suggestions, selectedName, select }
}

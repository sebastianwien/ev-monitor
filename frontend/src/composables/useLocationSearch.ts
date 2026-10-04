import { ref } from 'vue'

export interface PlaceSuggestion {
  place_id: number; lat: string; lon: string; display_name: string
  /** Adressteile (addressdetails=1) */
  address?: Partial<Record<string, string>>
}
export interface PickedPlace { latitude: number; longitude: number; name: string }

/** Ein Aufruf für alle Stellen, die Nominatim befragen (Wizard, altes Formular, Preis-Nachtrag). */
export const nominatimSearchUrl = (q: string) =>
  `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&addressdetails=1&limit=5`

/**
 * Kurzer Name statt "140, Storkower Straße, Prenzlauer Berg, Pankow, Berlin, 10407, Deutschland":
 * Straße mit Hausnummer und Ort. Nur Beschriftung - der Ort des Logs sind die Koordinaten.
 */
export function shortAddress(s: PlaceSuggestion): string {
  const a = s.address
  const place = a?.city ?? a?.town ?? a?.village ?? a?.municipality
  if (a?.road) return [[a.road, a.house_number].filter(Boolean).join(' '), place].filter(Boolean).join(', ')
  return s.display_name.split(',').map(p => p.trim()).filter(Boolean).slice(0, 2).join(', ')
}

/**
 * Adresssuche über Nominatim (OpenStreetMap), ausdrücklich per Tap, nie beim Tippen: die
 * Nutzungsrichtlinie verbietet Autocomplete. Für Orte ohne Säule im Register (Freund, Hotel,
 * Ausland). Die Koordinaten des gewählten Orts gehen nur an das eigene Backend, das sie in
 * eine Geohash-Zelle umrechnet.
 */
export function useLocationSearch() {
  const query = ref('')
  const suggestions = ref<PlaceSuggestion[]>([])
  const selectedName = ref('')
  const loading = ref(false)
  /** Suche beantwortet, aber leer */
  const noResults = ref(false)

  const search = async (q: string) => {
    loading.value = true
    noResults.value = false
    try {
      const res = await fetch(nominatimSearchUrl(q))
      const data = await res.json()
      suggestions.value = Array.isArray(data) ? data : []
      noResults.value = suggestions.value.length === 0
    } catch {
      suggestions.value = []
    } finally {
      loading.value = false
    }
  }

  const select = (s: PlaceSuggestion): PickedPlace => {
    const name = shortAddress(s)
    selectedName.value = name
    query.value = name
    suggestions.value = []
    return { latitude: parseFloat(s.lat), longitude: parseFloat(s.lon), name }
  }

  const reset = () => { suggestions.value = []; noResults.value = false }

  return { query, suggestions, selectedName, loading, noResults, search, select, reset }
}

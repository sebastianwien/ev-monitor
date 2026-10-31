import { computed, ref } from 'vue'

export interface PlaceSuggestion {
  place_id: number; lat: string; lon: string; display_name: string
  /** Adressteile (addressdetails=1) */
  address?: Partial<Record<string, string>>
}
export interface PickedPlace { latitude: number; longitude: number; name: string }

/** Nominatim für die Stellen mit ausdrücklicher Suche per Tap (altes Formular, Preis-Nachtrag, Sprache): die Nutzungsrichtlinie verbietet Autocomplete. */
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

/** Photon (komoot, OSM-Daten): für Suche beim Tippen gebaut, anders als Nominatim. Fair Use, kein Schlüssel. */
export const PHOTON_URL = 'https://photon.komoot.io/api/'
export interface LatLon { lat: number; lon: number }
/** Eine Nachkommastelle, rund 10 km: genug für "Treffer in der Nähe zuerst", ohne die genaue Position an komoot zu geben */
const coarse = (v: number) => Math.round(v * 10) / 10
export const photonSearchUrl = (q: string, near?: LatLon | null) =>
  `${PHOTON_URL}?q=${encodeURIComponent(q)}&limit=5` + (near ? `&lat=${coarse(near.lat)}&lon=${coarse(near.lon)}` : '')

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: Partial<Record<'name' | 'street' | 'housenumber' | 'postcode' | 'city' | 'district' | 'county' | 'state' | 'country' | 'type' | 'osm_type', string>> & { osm_id?: number }
}
const OSM_TYPE: Record<string, number> = { N: 1, W: 2, R: 3 }

/** Photon-Treffer im Format der übrigen Suche (Nominatim), damit Kurzname, letzte Adressen und Zeilen gleich bleiben */
export function fromPhoton(f: PhotonFeature): PlaceSuggestion {
  const p = f.properties
  const road = p.street ?? (p.type === 'street' ? p.name : undefined)
  const street = road ? [road, p.housenumber].filter(Boolean).join(' ') : undefined
  const named = p.name && p.name !== road ? p.name : undefined
  const town = [p.postcode, p.city].filter(Boolean).join(' ') || undefined
  const [lon, lat] = f.geometry.coordinates
  return {
    place_id: (p.osm_id ?? 0) * 10 + (OSM_TYPE[p.osm_type ?? ''] ?? 0),
    lat: String(lat), lon: String(lon),
    display_name: [named, street, town, p.country].filter(Boolean).join(', '),
    address: { road, house_number: p.housenumber, city: p.city ?? p.district, postcode: p.postcode, country: p.country },
  }
}

const MIN_CHARS = 3
const DEBOUNCE_MS = 400

/**
 * Adresssuche über Photon, beim Tippen entprellt (ab drei Zeichen, eine Anfrage je Tipp-Pause), überholte
 * Antworten werden verworfen. Für Orte ohne Säule im Register (Freund, Hotel, Ausland). `near` bevorzugt
 * Treffer nahe der eigenen Position. Die Koordinaten des gewählten Orts gehen nur an das eigene Backend,
 * das sie in eine Geohash-Zelle umrechnet.
 */
export function useLocationSearch(near: () => LatLon | null = () => null) {
  const query = ref('')
  const suggestions = ref<PlaceSuggestion[]>([])
  const selectedName = ref('')
  const loading = ref(false)
  /** Suche beantwortet, aber leer */
  const noResults = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let requestNo = 0
  /** Tipp-Pause läuft: die Vorschläge gehören noch zur vorigen Eingabe */
  const waiting = ref(false)
  /** Vorschläge veraltet (Pause läuft oder Antwort steht aus): ein Tap darauf träfe den falschen Ort */
  const pending = computed(() => waiting.value || loading.value)

  const search = async (q: string) => {
    if (timer) { clearTimeout(timer); timer = null }
    waiting.value = false
    const mine = ++requestNo
    loading.value = true
    noResults.value = false
    try {
      const res = await fetch(photonSearchUrl(q, near()))
      // Drossel (429) oder Serverfehler ist kein "kein Treffer"
      if (!res.ok) throw new Error(`Photon ${res.status}`)
      const data = await res.json()
      if (mine !== requestNo) return
      suggestions.value = Array.isArray(data?.features) ? data.features.map(fromPhoton) : []
      noResults.value = suggestions.value.length === 0
    } catch {
      if (mine === requestNo) suggestions.value = []
    } finally {
      if (mine === requestNo) loading.value = false
    }
  }

  /** Beim Tippen: nach der Pause suchen; zu kurzer Text leert die Liste */
  const autocomplete = (q: string) => {
    if (timer) clearTimeout(timer)
    const text = q.trim()
    if (text.length < MIN_CHARS) { reset(); return }
    waiting.value = true
    timer = setTimeout(() => { timer = null; search(text) }, DEBOUNCE_MS)
  }

  const select = (s: PlaceSuggestion): PickedPlace => {
    const name = shortAddress(s)
    selectedName.value = name
    query.value = name
    reset()
    return { latitude: parseFloat(s.lat), longitude: parseFloat(s.lon), name }
  }

  const reset = () => {
    if (timer) { clearTimeout(timer); timer = null }
    waiting.value = false
    requestNo++; suggestions.value = []; noResults.value = false; loading.value = false
  }

  return { query, suggestions, selectedName, loading, pending, noResults, search, autocomplete, select, reset }
}

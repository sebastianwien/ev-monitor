import { nominatimSearchUrl, shortAddress, type PickedPlace, type PlaceSuggestion } from '../../composables/useLocationSearch'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceChoice } from './wizardLogic'

/**
 * Gesprochene Adresse auf Koordinaten: ein Nominatim-Aufruf, ausgelöst durch die Aufnahme des
 * Nutzers (kein Autocomplete, Policy-konform). Erster Treffer, sonst null. Die Koordinaten gehen wie
 * bei der getippten Suche nur ans eigene Backend, das sie in eine Geohash-Zelle umrechnet.
 */
export async function geocodeSpokenAddress(q: string): Promise<PickedPlace | null> {
  try {
    const res = await fetch(nominatimSearchUrl(q))
    const data: PlaceSuggestion[] = await res.json()
    const hit = Array.isArray(data) ? data[0] : null
    if (!hit) return null
    return { latitude: parseFloat(hit.lat), longitude: parseFloat(hit.lon), name: shortAddress(hit) }
  } catch {
    return null
  }
}

const norm = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{N}]/gu, '')

/**
 * Ort an einer gesprochenen Adresse. Mit Betreiber: dessen Säule dort, sonst der Betreiber frei.
 * Ohne Betreiber: privat - wer eine Adresse diktiert, lädt meist bei Freunden, Hotel oder Firma.
 * unsure: Ort zur Prüfung markieren, weil Säulen in der Nähe stehen bzw. die genannte fehlt.
 */
export function addressPlace(stations: StationMatch[], operator: string | null): { choice: PlaceChoice; unsure: boolean } {
  const op = operator ? norm(operator) : ''
  if (op) {
    const hit = stations.find(s => { const n = norm(s.name); return n.includes(op) || op.includes(n) })
    return hit ? { choice: { kind: 'station', station: hit }, unsure: false } : { choice: { kind: 'other', cpoName: operator }, unsure: true }
  }
  return { choice: { kind: 'home' }, unsure: stations.length > 0 }
}

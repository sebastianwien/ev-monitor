import { nominatimSearchUrl, shortAddress, type PickedPlace, type PlaceSuggestion } from '../../composables/useLocationSearch'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceChoice, VoiceDraft } from './wizardLogic'

/** Halbe Kantenlänge des Suchfensters um die bekannte Position, in Grad (rund 50 km) */
const NEAR_DEGREES = 0.5

/**
 * Gesprochene Adresse auf Koordinaten: ein Nominatim-Aufruf, ausgelöst durch die Aufnahme des
 * Nutzers (kein Autocomplete, Policy-konform). Mit bekannter Position bevorzugt Nominatim Treffer
 * in deren Umgebung (viewbox ohne bounded: weiter entfernte Orte bleiben möglich). Erster Treffer, sonst null.
 */
export async function geocodeSpokenAddress(q: string, near?: { lat: number; lon: number } | null): Promise<PickedPlace | null> {
  const box = near
    ? `&viewbox=${near.lon - NEAR_DEGREES},${near.lat + NEAR_DEGREES},${near.lon + NEAR_DEGREES},${near.lat - NEAR_DEGREES}`
    : ''
  try {
    const res = await fetch(nominatimSearchUrl(q) + box)
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
    const hit = stations.find(s => { const n = norm(s.name); return !!n && (n.includes(op) || op.includes(n)) })
    return hit ? { choice: { kind: 'station', station: hit }, unsure: false } : { choice: { kind: 'other', cpoName: operator }, unsure: true }
  }
  return { choice: { kind: 'home' }, unsure: stations.length > 0 }
}

/** Ob der Ort aus der Adresse kommt: Adresse gesagt und kein konkreter Listentreffer (das Backend lässt nur genannte stehen). */
export const placeFromAddress = (d: VoiceDraft): boolean =>
  !!d.fields.spokenAddress && (!d.place || d.place.kind === 'other')

/**
 * Gemeinsamer Weg für Wizard und Bearbeiten: Adresse suchen, Säulen dort laden, Ort wählen.
 * null = Adresse nicht gefunden; der Aufrufer zeigt den Hinweis und lässt den Ort offen.
 */
export async function resolveSpokenAddress(d: VoiceDraft, opts: {
  near?: { lat: number; lon: number } | null
  loadStations: (lat: number, lon: number) => Promise<StationMatch[]>
}): Promise<{ hit: PickedPlace; choice: PlaceChoice; unsure: boolean } | null> {
  const hit = await geocodeSpokenAddress(d.fields.spokenAddress!, opts.near)
  if (!hit) return null
  const stations = await opts.loadStations(hit.latitude, hit.longitude)
  return { hit, ...addressPlace(stations, d.place?.cpoName ?? null) }
}

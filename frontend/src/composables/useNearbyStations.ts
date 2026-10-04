import { computed, ref, shallowRef } from 'vue'
import api from '../api/axios'

/** Ein Ladestandort aus dem Ladesäulenregister: ein Betreiber an einer Geohash-Zelle. */
export interface StationMatch {
  /** Kanonischer Ladenetz-Name, sonst Rohname aus dem Register */
  name: string
  /** Ob der Name einem bekannten Ladenetz zugeordnet werden konnte */
  known: boolean
  /** Höchste AC- bzw. DC-Steckerleistung laut Register; DC null = kein Schnelllader */
  maxAcKw: number | null
  maxDcKw: number | null
  fastCharging: boolean
  chargePoints: number
  /** "Straße Nr, PLZ Ort" der nächsten Säule, null wenn das Register keine Adresse führt */
  address: string | null
  plugTypes: string[]
  registerId: number | null
  /** Zelle (7 Stellen) der nächsten Säule dieses Betreibers - identifiziert den Ladestandort */
  geohash: string
  /** Exakte Position der nächsten Säule laut Register - nur zur Anzeige (Minimap), wird nie gespeichert. */
  latitude?: number | null
  longitude?: number | null
}

/** Standort im Umkreis, zusätzlich mit Entfernung. */
export interface NearbyStation extends StationMatch {
  /** Entfernung zum Mittelpunkt der Geohash-Zelle, nicht zur Nutzerposition */
  distanceMeters: number
}

/**
 * Ladestandorte in der Nähe für die Ortswahl im Log-Formular. Die Koordinaten gehen nur
 * an das eigene Backend, das sie sofort in eine Geohash-Zelle umrechnet.
 */
export const DEFAULT_RADIUS_M = 250
export const WIDE_RADIUS_M = 2500
/** Stufen des wachsenden Umkreises: ist eine Stufe leer, kommt automatisch die nächste. */
export const RADIUS_STEPS = [DEFAULT_RADIUS_M, 1000, WIDE_RADIUS_M] as const

export function useNearbyStations() {
  const stations = ref<NearbyStation[]>([])
  const loading = ref(false)
  /** Umkreis der aktuellen Liste; während der Suche der gerade abgefragte */
  const radius = ref<number>(DEFAULT_RADIUS_M)
  /** Die letzte Suche lief ohne Fehler bis zur größten Stufe und fand nichts */
  const exhausted = ref(false)
  // Reaktiv, sonst bliebe canExpand auf dem Stand vor der ersten Suche stehen
  const last = shallowRef<{ lat: number; lon: number } | null>(null)
  let seq = 0

  /** Sucht ab Stufe `from` und erweitert, bis eine Stufe Treffer hat. Ein Fehler beendet die Suche. */
  const search = async (lat: number, lon: number, from: number) => {
    const mine = ++seq
    last.value = { lat, lon }
    loading.value = true
    exhausted.value = false
    try {
      for (let i = from; i < RADIUS_STEPS.length; i++) {
        const r = RADIUS_STEPS[i]
        radius.value = r
        // Ohne Radius-Parameter bleibt der Standardaufruf (und sein Cache) unverändert
        const params = r === DEFAULT_RADIUS_M ? { lat, lon } : { lat, lon, radius: r }
        const res = await api.get('/charging-provider-tariffs/cpos/nearby-stations', { params })
        if (mine !== seq) return
        stations.value = Array.isArray(res.data) ? res.data : []
        if (stations.value.length) return
      }
      exhausted.value = true
    } catch {
      if (mine === seq) stations.value = []
    } finally {
      if (mine === seq) loading.value = false
    }
  }

  const load = (lat: number, lon: number) => search(lat, lon, 0)
  const stepIndex = computed(() => RADIUS_STEPS.indexOf(radius.value as typeof RADIUS_STEPS[number]))
  /** Nächste Stufe für "Nicht dabei?", null wenn die größte schon erreicht ist */
  const nextRadius = computed<number | null>(() => RADIUS_STEPS[stepIndex.value + 1] ?? null)
  /** "Nicht dabei?": dieselbe Position eine Stufe weiter, das Ergebnis ersetzt die Liste. */
  const expand = async () => { const at = last.value; if (at && nextRadius.value) await search(at.lat, at.lon, stepIndex.value + 1) }
  const canExpand = computed(() => last.value != null && nextRadius.value != null)

  return { stations, loading, radius, nextRadius, exhausted, canExpand, load, expand }
}

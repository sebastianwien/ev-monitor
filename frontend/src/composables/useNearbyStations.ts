import { computed, ref } from 'vue'
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

export function useNearbyStations() {
  const stations = ref<NearbyStation[]>([])
  const loading = ref(false)
  /** Umkreis der aktuellen Liste; nach "Umkreis erweitern" der weite */
  const radius = ref(DEFAULT_RADIUS_M)
  let last: { lat: number; lon: number } | null = null
  let seq = 0

  const load = async (lat: number, lon: number, r: number = DEFAULT_RADIUS_M) => {
    const mine = ++seq
    last = { lat, lon }
    loading.value = true
    try {
      // Ohne Radius-Parameter bleibt der Standardaufruf (und sein Cache) unverändert
      const params = r === DEFAULT_RADIUS_M ? { lat, lon } : { lat, lon, radius: r }
      const res = await api.get('/charging-provider-tariffs/cpos/nearby-stations', { params })
      if (mine !== seq) return
      stations.value = Array.isArray(res.data) ? res.data : []
      radius.value = r
    } catch {
      if (mine === seq) stations.value = []
    } finally {
      if (mine === seq) loading.value = false
    }
  }

  /** "Nicht dabei?": dieselbe Position im weiten Umkreis, das Ergebnis ersetzt die Liste. */
  const expand = async () => { if (last) await load(last.lat, last.lon, WIDE_RADIUS_M) }
  const canExpand = computed(() => last != null && radius.value < WIDE_RADIUS_M)

  return { stations, loading, radius, canExpand, load, expand }
}

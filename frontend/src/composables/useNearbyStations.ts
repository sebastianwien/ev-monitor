import { ref } from 'vue'
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
export function useNearbyStations() {
  const stations = ref<NearbyStation[]>([])
  const loading = ref(false)

  const load = async (lat: number, lon: number) => {
    loading.value = true
    try {
      const res = await api.get('/charging-provider-tariffs/cpos/nearby-stations', { params: { lat, lon } })
      stations.value = Array.isArray(res.data) ? res.data : []
    } catch {
      stations.value = []
    } finally {
      loading.value = false
    }
  }

  return { stations, loading, load }
}

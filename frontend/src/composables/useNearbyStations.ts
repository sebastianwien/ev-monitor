import { ref } from 'vue'
import api from '../api/axios'

/** Ein Ladestandort aus dem Ladesäulenregister, wie ihn das Backend im Umkreis liefert. */
export interface NearbyStation {
  /** Kanonischer Ladenetz-Name, sonst Rohname aus dem Register */
  name: string
  /** Ob der Name einem bekannten Ladenetz zugeordnet werden konnte */
  known: boolean
  /** Entfernung zum Mittelpunkt der Geohash-Zelle, nicht zur Nutzerposition */
  distanceMeters: number
  maxPowerKw: number | null
  fastCharging: boolean
  chargePoints: number
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

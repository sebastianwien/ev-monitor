import { ref } from 'vue'
import api from '../api/axios'

/** Ein Ladestandort aus dem Register, an dem der Nutzer schon geladen hat. */
export interface RecentSite {
  id: string
  name: string
  cpoName: string | null
  geohash: string
  maxAcKw: number | null
  maxDcKw: number | null
  chargePoints: number
  fastCharging: boolean
  address: string | null
  plugTypes: string[]
  lastUsedAt: string
  usageCount: number
}

/**
 * Ein Ort, an dem der Nutzer schon geladen hat - eine Zelle aus seinen eigenen Logs, wie sie
 * GET /charging-sites/known liefert. Mit Säule, wenn eine Register-Säule verknüpft war, sonst
 * mit dem Ortsteil aus dem Geocoder (kann fehlen).
 */
export interface KnownPlace {
  geohash: string
  isPublic: boolean
  usageCount: number
  lastUsedAt: string
  /** Anbieter der letzten Ladung dort, null ohne Anbieter */
  cpoName: string | null
  /** Ladekarte der letzten Ladung dort, null ohne Karte */
  lastProviderId: string | null
  /** Ortsteil, Dorf oder Stadt der Zellmitte; null wenn unbekannt oder eine Säule den Namen stellt */
  placeName: string | null
  site: RecentSite | null
  /** Entfernung der Position zur Zellmitte, null ohne Position */
  distanceMeters: number | null
  /** Die Position liegt in dieser Zelle */
  here: boolean
}

/**
 * Bekannte Orte für den Ortsschritt, häufigste zuerst. Mit Position stehen die Orte "hier" vorn;
 * die Koordinaten gehen nur ans eigene Backend zum Vergleich mit den eigenen Logs.
 */
export function useKnownPlaces() {
  const places = ref<KnownPlace[]>([])
  let seq = 0

  const load = async (lat?: number, lon?: number) => {
    const mine = ++seq
    try {
      const params = lat != null && lon != null ? { lat, lon } : undefined
      const res = await api.get('/charging-sites/known', { params })
      if (mine !== seq) return
      places.value = Array.isArray(res.data) ? res.data : []
    } catch {
      if (mine === seq) places.value = []
    }
  }

  return { places, load }
}

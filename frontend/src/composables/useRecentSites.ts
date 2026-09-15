import { ref } from 'vue'
import api from '../api/axios'

/** Ein Ladestandort, an dem der Nutzer schon geladen hat - aus GET /charging-sites/recent. */
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

/** Zuletzt genutzte Ladestandorte für den Ortsschritt, zuletzt genutzte zuerst. */
export function useRecentSites() {
  const sites = ref<RecentSite[]>([])

  const load = async () => {
    try {
      const res = await api.get('/charging-sites/recent')
      sites.value = Array.isArray(res.data) ? res.data : []
    } catch {
      sites.value = []
    }
  }

  return { sites, load }
}

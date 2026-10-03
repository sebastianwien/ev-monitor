import ngeohash from 'ngeohash'
import type { RecentSite } from '../../composables/useRecentSites'

/** Ab dieser Ungenauigkeit der Ortung sagt die Ortswahl es offen, statt "keine Säule" zu melden. */
export const INACCURATE_LOCATION_M = 500

export function isLocationInaccurate(accuracyMeters: number | null | undefined): boolean {
  return accuracyMeters != null && accuracyMeters > INACCURATE_LOCATION_M
}

const EARTH_RADIUS_M = 6_371_000
const rad = (deg: number) => (deg * Math.PI) / 180

function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
    + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export interface SiteWithDistance { site: RecentSite; distanceMeters: number | null }

/**
 * Die letzten Ladeorte nach Entfernung zur Position: wer nachträglich einträgt, hat meist an
 * einem davon geladen. Rechnet nur im Browser aus dem Geohash, es geht nichts ans Backend.
 */
export function sitesByDistance(sites: RecentSite[], lat: number | null, lon: number | null): SiteWithDistance[] {
  if (lat == null || lon == null) return sites.map(site => ({ site, distanceMeters: null }))
  return sites
    .map(site => {
      const c = ngeohash.decode(site.geohash)
      return { site, distanceMeters: Math.round(distanceMeters(lat, lon, c.latitude, c.longitude)) }
    })
    .sort((a, b) => a.distanceMeters - b.distanceMeters)
}

export function formatDistance(meters: number, locale?: string): string {
  return meters < 1000
    ? `${Math.round(meters)} m`
    : `${(meters / 1000).toLocaleString(locale, { maximumFractionDigits: 1 })} km`
}

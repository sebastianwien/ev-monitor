import geohash from 'ngeohash'
import { CELL_PRECISION, type LatLon } from '../utils/stationMap'

/**
 * Zuletzt gezeigte Mitte der Säulenkarte, damit sie beim nächsten Öffnen sofort dort steht.
 * Nur auf dem Gerät und nur auf ein Kartenfeld gerundet (rund 1 km), nie die Position.
 * Speicher kann gesperrt sein (privates Fenster): dann ohne Gedächtnis.
 */
export const LAST_CENTER_KEY = 'stations.mapCenter'
const CELL = /^[0-9b-hjkmnp-z]{6}$/

export function readLastMapCenter(): LatLon | null {
  try {
    const cell = localStorage.getItem(LAST_CENTER_KEY)
    if (!cell || !CELL.test(cell)) return null
    const { latitude, longitude } = geohash.decode(cell)
    return { lat: latitude, lon: longitude }
  } catch {
    return null
  }
}

export function rememberMapCenter(lat: number, lon: number): void {
  try {
    localStorage.setItem(LAST_CENTER_KEY, geohash.encode(lat, lon, CELL_PRECISION))
  } catch {
    // Speicher gesperrt: die Karte öffnet dann am Standort oder in der Mitte Deutschlands
  }
}

/**
 * Turns the coarse trip geohashes into map coordinates.
 *
 * The backend only ever hands out geohash-6 (~1.2 km x 0.6 km) and only for the most
 * recent trip - there is no recorded route. Everything drawn from this is therefore an
 * approximate start and end area plus a straight line, never the driven road.
 */
import geohash from 'ngeohash'

export interface LatLon {
  lat: number
  lon: number
}

export interface TripMapView {
  start: LatLon | null
  end: LatLon | null
  /** Start and end share one cell - a connecting line would pretend a precision we lack. */
  roundTrip: boolean
  /** Half diagonal of the geohash cell in metres: the honest blur around each point. */
  cellRadiusMeters: number
}

const GEOHASH_ALPHABET = /^[0-9bcdefghjkmnpqrstuvwxyz]+$/
const EARTH_RADIUS_M = 6_371_000

function decode(value: string | null | undefined): LatLon | null {
  if (!value || !GEOHASH_ALPHABET.test(value)) return null
  try {
    const { latitude, longitude } = geohash.decode(value)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
    return { lat: latitude, lon: longitude }
  } catch {
    return null
  }
}

/** Half the diagonal of the cell the geohash describes, in metres. */
function cellRadius(value: string): number {
  const [minLat, minLon, maxLat, maxLon] = geohash.decode_bbox(value)
  const midLat = ((minLat + maxLat) / 2) * (Math.PI / 180)
  const heightM = ((maxLat - minLat) * Math.PI / 180) * EARTH_RADIUS_M
  const widthM = ((maxLon - minLon) * Math.PI / 180) * EARTH_RADIUS_M * Math.cos(midLat)
  return Math.sqrt(heightM * heightM + widthM * widthM) / 2
}

export function tripMapView(
  startGeohash: string | null | undefined,
  endGeohash: string | null | undefined,
): TripMapView | null {
  const start = decode(startGeohash)
  const end = decode(endGeohash)
  if (!start && !end) return null

  const roundTrip = start != null && end != null && startGeohash === endGeohash
  return {
    start,
    end: roundTrip ? null : end,
    roundTrip,
    cellRadiusMeters: cellRadius((start ? startGeohash : endGeohash) as string),
  }
}

/**
 * Turns the coarse trip geohashes into map coordinates.
 *
 * Der Backend-Response traegt die Hashes nur fuer die neueste Fahrt und nur bis Praezision 8
 * (~38 x 19 m). Was hieraus entsteht, ist deshalb immer eine Start- und eine Zielgegend, nie
 * ein Punkt - und die Verbindung zwischen ihnen ist eine Luftlinie, keine Strecke. Die
 * gefahrene Linie kommt getrennt als tracePolyline.
 */
import geohash from 'ngeohash'
import { decodePolyline } from './polyline'

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

/** Woher die gezeichnete Linie stammt - davon haengen Strichbild und Namensnennung ab. */
export interface TripLine {
  points: [number, number][]
  /**
   * 'matched' = die aufgezeichnete Spur, vom Router auf echte Strassen gelegt;
   * 'trace' = dieselbe Spur roh, mit geraden Verbindungen zwischen den Stuetzpunkten;
   * 'sketch' = ein blosser Vorschlag zwischen Start- und Zielgegend, ohne Bezug zur Fahrt.
   */
  source: 'matched' | 'trace' | 'sketch'
}

/**
 * Waehlt die Linie einer Fahrt in dieser Rangfolge:
 *
 * 1. die auf Strassen gelegte Spur - dieselbe Messung, nur ohne die Geraden quer durch Blocks,
 * 2. die rohe Spur, wenn das Matching fehlt oder nichts hergab,
 * 3. die Skizze zwischen den Enden, die von der Fahrt selbst nichts weiss.
 *
 * Ohne alle drei bleibt es beim Aufrufer, die Luftlinie zu ziehen.
 *
 * @param routeKind woher {@link routePolyline} stammt; ohne 'MATCHED' ist sie nur eine Skizze
 */
export function tripLine(
  tracePolyline: string | null | undefined,
  routePolyline: string | null | undefined,
  routeKind: string | null | undefined,
): TripLine | null {
  const route = decodePolyline(routePolyline)
  if (routeKind === 'MATCHED' && route.length >= 2) return { points: route, source: 'matched' }
  const trace = decodePolyline(tracePolyline)
  if (trace.length >= 2) return { points: trace, source: 'trace' }
  if (route.length >= 2) return { points: route, source: 'sketch' }
  return null
}

/**
 * Ob eine Fahrt genug Ortsbezug fuer eine Karte mitbringt.
 *
 * Der Server entscheidet das, nicht der Client: aeltere Fahrten kommen ohne Ortsangaben
 * an, solange der Nutzer die Analytics-Freischaltung nicht hat. Fehlt die Gegend, waere
 * die Karte leer - eine Route allein hat nichts, woran sie haengt.
 */
export interface TripMapSource {
  locationStartGeohash?: string | null
  locationEndGeohash?: string | null
  tracePolyline?: string | null
  /** Ohne Wirkung auf {@link hasTripMap}: die Route haengt an den Geohashes, die dort fehlen. */
  routePolyline?: string | null
  /** Woher {@link routePolyline} stammt - siehe {@link tripLine}. */
  routeKind?: string | null
  /** Gefahrene Strecke; unter 0,1 km zeigt die Minikarte keine Bewegung, auch wenn die Spur einen Ausreißer hat */
  distanceKm?: number | null
}

export function hasTripMap(trip: TripMapSource | null | undefined): boolean {
  return !!(trip?.locationStartGeohash || trip?.locationEndGeohash || trip?.tracePolyline)
}

/** Beide gerechneten Formen stammen vom Router - seine Nennung (CC-BY-SA) haengt an ihnen, nicht an der rohen Spur. */
export function isRoutedLine(line: TripLine | null): boolean {
  return line?.source === 'sketch' || line?.source === 'matched'
}

/** Gesammelte Nennung unter Kartenvorschauen ohne eigene Attribution: OSM immer, der Router nur, wenn eine Linie von ihm stammt. */
export function mapCredit(routed: boolean): string {
  return routed ? '© OpenStreetMap · Route © openrouteservice by HeiGIT' : '© OpenStreetMap'
}

/** Ein Hinweis für mehrere Minikarten (Tagesablauf, Logbuch): null, wenn keine Fahrt eine Minikarte zeigt. */
export function tripsCredit(trips: readonly TripMapSource[]): string | null {
  const mapped = trips.filter(hasTripThumb)
  return mapped.length ? mapCredit(mapped.some(tr => isRoutedLine(tripLine(tr.tracePolyline, tr.routePolyline, tr.routeKind)))) : null
}

/** Unter dieser Ausdehnung zeigt eine Minikarte keine Bewegung, nur einen Punkt auf der Stelle. */
const THUMB_MIN_MOVE_M = 100

/** Abstand zweier Punkte in Metern, flach genähert (für ein paar Kilometer genau genug). */
function meters(a: LatLon, b: LatLon): number {
  const midLat = ((a.lat + b.lat) / 2) * (Math.PI / 180)
  const dLat = (b.lat - a.lat) * (Math.PI / 180) * EARTH_RADIUS_M
  const dLon = (b.lon - a.lon) * (Math.PI / 180) * EARTH_RADIUS_M * Math.cos(midLat)
  return Math.sqrt(dLat * dLat + dLon * dLon)
}

/** Luftlinie zwischen zwei Geohashes in km, null wenn einer fehlt oder ungültig ist. */
export function geohashDistanceKm(a: string | null | undefined, b: string | null | undefined): number | null {
  const p = decode(a)
  const q = decode(b)
  return p && q ? meters(p, q) / 1000 : null
}

/**
 * Minikarte in einer Zeile nur, wenn darauf Bewegung zu sehen ist: die Linie reicht weiter als
 * {@link THUMB_MIN_MOVE_M}, ohne Linie liegen Start und Ziel so weit auseinander. Eine Fahrt auf der
 * Stelle (Rangieren, 0 km) bekommt keine. Die große Karte im Detail bleibt bei {@link hasTripMap}.
 */
export function hasTripThumb(trip: TripMapSource): boolean {
  if (!hasTripMap(trip)) return false
  if (trip.distanceKm != null && trip.distanceKm * 1000 < THUMB_MIN_MOVE_M) return false
  const line = tripLine(trip.tracePolyline, trip.routePolyline, trip.routeKind)
  if (line) {
    const first = { lat: line.points[0][0], lon: line.points[0][1] }
    return line.points.some(([lat, lon]) => meters(first, { lat, lon }) >= THUMB_MIN_MOVE_M)
  }
  const view = tripMapView(trip.locationStartGeohash, trip.locationEndGeohash)
  return !!view?.start && !!view.end && meters(view.start, view.end) >= THUMB_MIN_MOVE_M
}

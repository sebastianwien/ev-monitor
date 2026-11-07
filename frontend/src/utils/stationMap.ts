import geohash from 'ngeohash'

/**
 * Reine Logik der Säulenkarte: welche Kartenfelder ein Ausschnitt braucht, Entfernungen,
 * Anzeigeformate. Die Karte schickt nur Geohash-6-Felder (in Deutschland etwa 0,75 × 0,6 km) an den Server,
 * nie eine Position.
 */

export const CELL_PRECISION = 6
/** Mehr Felder braucht kein Ausschnitt in Straßenzoom; darüber bittet die Karte ums Heranzoomen. */
export const MAX_CELLS = 12

export interface LatLon { lat: number; lon: number }
export interface Bounds { south: number; west: number; north: number; east: number }
export type ChargeType = 'AC' | 'DC'

const centerOf = (b: Bounds): LatLon => ({ lat: (b.south + b.north) / 2, lon: (b.west + b.east) / 2 })
const cellCenter = (cell: string): LatLon => {
  const { latitude, longitude } = geohash.decode(cell)
  return { lat: latitude, lon: longitude }
}

/** Haversine; im Umkreis weniger Kilometer mehr als genau genug. */
export function distanceMeters(a: LatLon, b: LatLon): number {
  const R = 6_371_000
  const toRad = (d: number) => d * Math.PI / 180
  const dLat = toRad(b.lat - a.lat)
  const dLon = toRad(b.lon - a.lon)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

/**
 * Die Geohash-6-Felder, die den Ausschnitt abdecken, das mittlere zuerst; null, wenn es mehr als
 * `max` sind. Vorher eine Schätzung, damit ein herausgezoomter Ausschnitt nicht tausende Felder aufzählt.
 */
export function cellsForBounds(b: Bounds, max = MAX_CELLS): string[] | null {
  const mid = centerOf(b)
  const [s, w, n, e] = geohash.decode_bbox(geohash.encode(mid.lat, mid.lon, CELL_PRECISION))
  const estimate = Math.ceil((b.north - b.south) / (n - s)) * Math.ceil((b.east - b.west) / (e - w))
  if (estimate > max * 2) return null
  const cells = geohash.bboxes(b.south, b.west, b.north, b.east, CELL_PRECISION)
  if (cells.length > max) return null
  return [...new Set(cells)].sort((x, y) => distanceMeters(mid, cellCenter(x)) - distanceMeters(mid, cellCenter(y)))
}

/** Die Felder rund um die gegebenen, ohne sie selbst: auf dem Land einmal mitgeladen, damit die Karte selten leer ist. */
export function neighborRing(cells: string[]): string[] {
  const own = new Set(cells)
  const ring = new Set<string>()
  for (const c of cells) for (const n of geohash.neighbors(c)) if (!own.has(n)) ring.add(n)
  return [...ring]
}

interface Positioned { latitude: number; longitude: number }

export function sortByDistance<T extends Positioned>(stations: T[], center: LatLon): T[] {
  const d = (s: T) => distanceMeters(center, { lat: s.latitude, lon: s.longitude })
  return [...stations].sort((a, b) => d(a) - d(b))
}

export function inBounds(s: Positioned, b: Bounds): boolean {
  return s.latitude >= b.south && s.latitude <= b.north && s.longitude >= b.west && s.longitude <= b.east
}

const numberLocale = (locale: string) => locale === 'en' ? 'en-GB' : 'de-DE'

/** "250 m", "1,1 km", ab 10 km ganze Kilometer. */
export function formatDistance(meters: number, locale: string): string {
  const rounded = Math.max(10, Math.round(meters / 10) * 10)
  if (rounded < 1000) return `${rounded} m`
  const km = meters / 1000
  const digits = km < 10 ? 1 : 0
  return `${km.toLocaleString(numberLocale(locale), { minimumFractionDigits: digits, maximumFractionDigits: digits })} km`
}

/** Preise an deutschen Säulen sind Euro, auch für Nutzer mit anderer Währung: so steht es an der Säule. */
export function formatEur(eur: number, locale: string): string {
  const n = eur.toLocaleString(numberLocale(locale), { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return locale === 'en' ? `€${n}` : `${n} €`
}

export const formatPricePerKwh = formatEur

interface PlugSource { maxAcKw: number | null; maxDcKw: number | null; chargePoints: number; plugTypes: string[] }

const DC_PLUGS = new Set(['CCS', 'CHAdeMO', 'Tesla'])

/** Stecker und höchste Leistung der gewählten Ladeart; DC-Stecker bei Schnellladen, die übrigen bei Normalladen. */
export function plugLine(s: PlugSource, type: ChargeType): { plugs: string; kw: number | null; points: number } {
  const plugs = s.plugTypes.filter(p => DC_PLUGS.has(p) === (type === 'DC'))
  const order = (p: string) => (p === 'Typ 2' || p === 'CCS' ? 0 : 1)
  return {
    plugs: [...plugs].sort((a, b) => order(a) - order(b)).join(', '),
    kw: type === 'DC' ? s.maxDcKw : s.maxAcKw,
    points: s.chargePoints,
  }
}

interface Fees { sessionFeeEur: number | null; blockingFeePerMin: number | null; blockingFeeAfterMin: number | null; blockingFeeCapEur: number | null }
export type FeeLine =
  | { kind: 'none' }
  | { kind: 'fees'; sessionFeeEur: number | null; blocking: { perMin: number; afterMin: number | null; capEur: number | null } | null }

/** Was neben dem Preis je kWh anfällt; "none", wenn weder Start- noch Blockiergebühr. */
export function feeLine(t: Fees): FeeLine {
  const session = t.sessionFeeEur && t.sessionFeeEur > 0 ? t.sessionFeeEur : null
  const blocking = t.blockingFeePerMin && t.blockingFeePerMin > 0
    ? { perMin: t.blockingFeePerMin, afterMin: t.blockingFeeAfterMin, capEur: t.blockingFeeCapEur }
    : null
  if (session == null && blocking == null) return { kind: 'none' }
  return { kind: 'fees', sessionFeeEur: session, blocking }
}

/** Jüngstes Prüfdatum (ISO) der gezeigten Tarife, null ohne Angabe. */
export function latestVerified(tariffs: { verifiedOn: string | null }[]): string | null {
  const dates = tariffs.map(t => t.verifiedOn).filter((d): d is string => !!d).sort()
  return dates.length ? dates[dates.length - 1] : null
}

/** iPhone und iPad: Apple Karten; sonst Google Maps, das auf Android die App öffnet. */
export function routeUrl(lat: number, lon: number, apple: boolean): string {
  return apple
    ? `https://maps.apple.com/?daddr=${lat},${lon}`
    : `https://www.google.com/maps/dir/?api=1&destination=${lat},${lon}`
}

/** iOS erkennen, auch iPadOS (meldet sich als Mac mit Touch). */
export function isAppleDevice(nav: Pick<Navigator, 'userAgent' | 'maxTouchPoints'> = navigator): boolean {
  return /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1)
}

export type StationsNotice = 'zoom' | 'search' | 'outside' | 'error' | 'empty' | null

export interface NoticeInput {
  /** Ausschnitt braucht mehr als zwölf Felder */
  tooWide: boolean
  located: 'pending' | 'ok' | 'denied'
  /** Karte öffnete an einer gemerkten Mitte */
  remembered: boolean
  outsideGermany: boolean
  inView: number
  /** Ein Feld blieb ohne Antwort (Register, Drossel) */
  failed: boolean
  viewLoaded: boolean
  ringLoaded: boolean
  /** Zeilen in der Liste, auch die nächsten aus den Nachbarfeldern */
  listed: number
}

/** Genau ein Satz im Sheet, als Handlung; null, wenn die Liste für sich spricht. */
export function stationsNotice(n: NoticeInput): StationsNotice {
  if (n.tooWide) return n.located === 'denied' && !n.remembered ? 'search' : 'zoom'
  if (n.outsideGermany && n.inView === 0) return 'outside'
  if (n.failed) return 'error'
  if (n.viewLoaded && n.ringLoaded && n.listed === 0) return 'empty'
  return null
}

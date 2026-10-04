import geohash from 'ngeohash'

/**
 * Kachel-URLs, die die Minimap für einen Ausschnitt um den Punkt laden wird - zum Vorladen in Schritt 1,
 * damit die Karte in Schritt 3 aus dem Browser-Cache steht statt kachelweise reinzuploppen.
 * Zoom und Subdomain-Wahl müssen Leaflet exakt entsprechen, sonst trifft der Cache nicht (anderer Host = andere URL).
 */
export const MINIMAP_ZOOM = 17
/** Private Ladung: die ganze Zelle (rund 0,75 x 0,6 km) muss ins Bild, daher weiter draußen */
export const MINIMAP_AREA_ZOOM = 14
/** Höchste Kopfhöhe: die Karte ist immer so hoch gerendert und wird nur beschnitten. Zahl und Klasse müssen zusammenpassen. */
export const MINIMAP_MAX_PX = 1000
export const MINIMAP_MAX_CLASS = 'h-[1000px]'
const TILE = 256
const SUBDOMAINS = 'abc'

export function minimapTileUrls(lat: number, lon: number, widthPx: number, heightPx: number, zoom = MINIMAP_ZOOM): string[] {
  const n = 2 ** zoom
  const rad = (lat * Math.PI) / 180
  // Weltpixel des Mittelpunkts (Web-Mercator)
  const cx = ((lon + 180) / 360) * n * TILE
  const cy = ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * n * TILE
  const x0 = Math.floor((cx - widthPx / 2) / TILE), x1 = Math.floor((cx + widthPx / 2 - 1) / TILE)
  const y0 = Math.floor((cy - heightPx / 2) / TILE), y1 = Math.floor((cy + heightPx / 2 - 1) / TILE)
  const urls: string[] = []
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const s = SUBDOMAINS[Math.abs(x + y) % SUBDOMAINS.length]
    urls.push(`https://${s}.tile.openstreetmap.org/${zoom}/${x}/${y}.png`)
  }
  return urls
}

const requested = new Set<string>()
/** Kacheln einmal anfordern und im Browser-Cache lassen; Fehler sind egal, dann lädt Leaflet sie eben selbst. */
export function prefetchMinimapTiles(lat: number, lon: number, widthPx: number, heightPx: number, zoom = MINIMAP_ZOOM): void {
  if (typeof Image === 'undefined') return
  for (const url of minimapTileUrls(lat, lon, widthPx, heightPx, zoom)) {
    if (requested.has(url)) continue
    requested.add(url)
    const img = new Image(); img.decoding = 'async'; img.src = url
  }
}

/** Fläche auf der Minimap mit ihrem Mittelpunkt */
export interface MinimapArea { south: number; west: number; north: number; east: number; lat: number; lon: number }
/** Privat gespeichert wird nur die Zelle (Geohash 6 Stellen, wie im Backend) - die Karte zeigt genau sie, nie die Position darin */
export const PRIVATE_GEOHASH_PRECISION = 6
export function privateArea(lat: number, lon: number): MinimapArea {
  const [south, west, north, east] = geohash.decode_bbox(geohash.encode(lat, lon, PRIVATE_GEOHASH_PRECISION))
  return { south, west, north, east, lat: (south + north) / 2, lon: (west + east) / 2 }
}

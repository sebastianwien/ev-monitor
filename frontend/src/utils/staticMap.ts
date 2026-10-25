/**
 * Stummes Kartenbild ohne Leaflet: OSM-Kacheln als Bilder nebeneinander, Punkte per Web-Mercator
 * auf Pixel gerechnet. Für Vorschauen, die nur zeigen und beim Antippen die echte Karte öffnen.
 * Subdomain wie Leaflet ({s} aus x + y), damit eine spätere Leaflet-Karte dieselben URLs aus dem Cache trifft.
 */
const TILE = 256
const SUBDOMAINS = 'abc'

export interface StaticMapPoint { lat: number; lon: number }
export interface StaticMapLayout {
  zoom: number
  /** Kachel mit linker oberer Ecke relativ zum Bild (kann negativ sein, das Bild schneidet ab). */
  tiles: Array<{ url: string; left: number; top: number }>
  /** Punkte in derselben Reihenfolge wie übergeben, in Bildpixeln. */
  points: Array<{ x: number; y: number }>
}

function worldPx(p: StaticMapPoint, zoom: number): { x: number; y: number } {
  const size = TILE * 2 ** zoom
  const rad = (p.lat * Math.PI) / 180
  return {
    x: ((p.lon + 180) / 360) * size,
    y: ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * size,
  }
}

/**
 * Größter Zoom bis maxZoom, bei dem alle Punkte mit `padding` Rand ins Bild passen; das Bild zentriert
 * auf die Mitte der Punkte. null ohne Punkte.
 */
export function staticMapLayout(points: StaticMapPoint[], width: number, height: number,
  opts: { maxZoom: number; minZoom?: number; padding: number }): StaticMapLayout | null {
  if (!points.length) return null
  const minZoom = opts.minZoom ?? 3
  let zoom = opts.maxZoom
  let px = points.map(p => worldPx(p, zoom))
  const span = (ps: Array<{ x: number; y: number }>) => ({
    w: Math.max(...ps.map(p => p.x)) - Math.min(...ps.map(p => p.x)),
    h: Math.max(...ps.map(p => p.y)) - Math.min(...ps.map(p => p.y)),
  })
  while (zoom > minZoom) {
    const s = span(px)
    if (s.w <= width - 2 * opts.padding && s.h <= height - 2 * opts.padding) break
    zoom--
    px = points.map(p => worldPx(p, zoom))
  }
  const cx = (Math.max(...px.map(p => p.x)) + Math.min(...px.map(p => p.x))) / 2
  const cy = (Math.max(...px.map(p => p.y)) + Math.min(...px.map(p => p.y))) / 2
  const ox = cx - width / 2
  const oy = cy - height / 2

  const tiles: StaticMapLayout['tiles'] = []
  for (let ty = Math.floor(oy / TILE); ty <= Math.floor((oy + height - 1) / TILE); ty++) {
    for (let tx = Math.floor(ox / TILE); tx <= Math.floor((ox + width - 1) / TILE); tx++) {
      const s = SUBDOMAINS[Math.abs(tx + ty) % SUBDOMAINS.length]
      tiles.push({ url: `https://${s}.tile.openstreetmap.org/${zoom}/${tx}/${ty}.png`, left: tx * TILE - ox, top: ty * TILE - oy })
    }
  }
  return { zoom, tiles, points: px.map(p => ({ x: p.x - ox, y: p.y - oy })) }
}

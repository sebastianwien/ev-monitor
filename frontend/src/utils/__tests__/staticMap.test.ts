import { describe, it, expect } from 'vitest'
import { staticMapLayout } from '../staticMap'

const HAMBURG = { lat: 53.55, lon: 10.0 }
const BERLIN = { lat: 52.52, lon: 13.4 }

describe('staticMapLayout', () => {
  it('ohne Punkte kein Bild', () => {
    expect(staticMapLayout([], 480, 200, { maxZoom: 12, padding: 30 })).toBeNull()
  })

  it('ein Punkt: größter erlaubter Zoom, Punkt in der Mitte', () => {
    const map = staticMapLayout([BERLIN], 480, 200, { maxZoom: 12, padding: 30 })!

    expect(map.zoom).toBe(12)
    expect(map.points[0].x).toBeCloseTo(240, 0)
    expect(map.points[0].y).toBeCloseTo(100, 0)
  })

  it('mehrere Punkte: der größte Zoom, bei dem alle mit Rand ins Bild passen', () => {
    const map = staticMapLayout([HAMBURG, BERLIN], 480, 200, { maxZoom: 12, padding: 30 })!

    for (const p of map.points) {
      expect(p.x).toBeGreaterThanOrEqual(30)
      expect(p.x).toBeLessThanOrEqual(450)
      expect(p.y).toBeGreaterThanOrEqual(30)
      expect(p.y).toBeLessThanOrEqual(170)
    }
    // eine Stufe näher passt es nicht mehr: der Abstand verdoppelt sich
    const dx = Math.abs(map.points[0].x - map.points[1].x)
    const dy = Math.abs(map.points[0].y - map.points[1].y)
    expect(dx * 2 > 480 - 60 || dy * 2 > 200 - 60).toBe(true)
  })

  it('Kacheln decken das ganze Bild ab, URL wie Leaflet (Subdomain aus x + y)', () => {
    const map = staticMapLayout([HAMBURG, BERLIN], 480, 200, { maxZoom: 12, padding: 30 })!

    expect(Math.min(...map.tiles.map(t => t.left))).toBeLessThanOrEqual(0)
    expect(Math.max(...map.tiles.map(t => t.left + 256))).toBeGreaterThanOrEqual(480)
    expect(Math.min(...map.tiles.map(t => t.top))).toBeLessThanOrEqual(0)
    expect(Math.max(...map.tiles.map(t => t.top + 256))).toBeGreaterThanOrEqual(200)
    for (const t of map.tiles) {
      const [, s, z, x, y] = t.url.match(/^https:\/\/([abc])\.tile\.openstreetmap\.org\/(\d+)\/(\d+)\/(\d+)\.png$/)!
      expect(Number(z)).toBe(map.zoom)
      expect(s).toBe('abc'[Math.abs(Number(x) + Number(y)) % 3])
    }
    expect(map.tiles.length).toBeLessThanOrEqual(6)
  })
})

import { describe, it, expect } from 'vitest'
import { minimapTileUrls, privateArea, MINIMAP_ZOOM, MINIMAP_AREA_ZOOM } from '../minimapTiles'

describe('minimapTileUrls', () => {
  it('liefert genau die Kacheln, die ein Ausschnitt um den Punkt berührt, mit Leaflets Subdomain-Regel', () => {
    // Berlin Mitte: Kachel 70416/42985 bei Zoom 17
    const urls = minimapTileUrls(52.5196, 13.4055, 412, 480)
    expect(MINIMAP_ZOOM).toBe(17)
    expect(urls.length).toBeGreaterThanOrEqual(4)
    expect(urls.length).toBeLessThanOrEqual(9)
    expect(urls.some(u => u.endsWith('/17/70416/42985.png'))).toBe(true)
    for (const u of urls) {
      const m = u.match(/^https:\/\/([abc])\.tile\.openstreetmap\.org\/17\/(\d+)\/(\d+)\.png$/)
      expect(m, u).not.toBeNull()
      const [, s, x, y] = m!
      expect(s).toBe('abc'[(Number(x) + Number(y)) % 3])
    }
    expect(new Set(urls).size).toBe(urls.length)
  })

  it('ein 256er Ausschnitt mitten in der Kachel braucht nur sie selbst', () => {
    // Kachelmitte zurückrechnen: x=70416.5, y=42985.5 bei 2^17
    const n = 2 ** 17
    const lon = (70416.5 / n) * 360 - 180
    const lat = (Math.atan(Math.sinh(Math.PI * (1 - 2 * 42985.5 / n))) * 180) / Math.PI
    expect(minimapTileUrls(lat, lon, 200, 200)).toHaveLength(1)
  })
})

describe('privateArea', () => {
  it('ist die Zelle, die das Backend für eine private Ladung speichert (Geohash 6 Stellen), samt Mittelpunkt', () => {
    const a = privateArea(52.5342, 13.4516)
    // Geohash 6: 0,010986° breit, 0,005493° hoch
    expect(a.east - a.west).toBeCloseTo(0.010986, 5)
    expect(a.north - a.south).toBeCloseTo(0.005493, 5)
    expect(a.south).toBeLessThanOrEqual(52.5342); expect(a.north).toBeGreaterThanOrEqual(52.5342)
    expect(a.west).toBeLessThanOrEqual(13.4516); expect(a.east).toBeGreaterThanOrEqual(13.4516)
    expect(a.lat).toBeCloseTo((a.south + a.north) / 2, 9)
    expect(a.lon).toBeCloseTo((a.west + a.east) / 2, 9)
  })

  it('Nachbarpunkte in derselben Zelle ergeben dieselbe Fläche: die Karte verrät nicht, wo in der Zelle', () => {
    const a = privateArea(52.5342, 13.4516)
    expect(privateArea(a.south + 0.0001, a.west + 0.0001)).toEqual(a)
  })
})

describe('minimapTileUrls mit Zoom', () => {
  it('lädt für die Fläche die Kacheln der weiteren Zoomstufe', () => {
    const urls = minimapTileUrls(52.5342, 13.4516, 390, 480, MINIMAP_AREA_ZOOM)
    expect(MINIMAP_AREA_ZOOM).toBe(14)
    expect(urls.length).toBeGreaterThan(0)
    for (const u of urls) expect(u).toMatch(/\.org\/14\/\d+\/\d+\.png$/)
  })
})

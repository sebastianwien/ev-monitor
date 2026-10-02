import { describe, it, expect } from 'vitest'
import { minimapTileUrls, MINIMAP_ZOOM } from '../minimapTiles'

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

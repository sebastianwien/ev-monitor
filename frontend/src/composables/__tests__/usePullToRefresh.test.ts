import { describe, it, expect } from 'vitest'
import { dampPull, shouldTriggerRefresh, PULL_THRESHOLD_PX } from '../usePullToRefresh'

describe('usePullToRefresh - reine Pull-Logik', () => {
  describe('dampPull', () => {
    it('ignoriert Aufwaerts-/Null-Bewegung (kein Pull)', () => {
      expect(dampPull(0)).toBe(0)
      expect(dampPull(-120)).toBe(0)
    })

    it('daempft die Roh-Distanz (Gummiband-Effekt)', () => {
      // Abwaertsziehen wird gedaempft zurueckgegeben, nie 1:1
      expect(dampPull(100)).toBeGreaterThan(0)
      expect(dampPull(100)).toBeLessThan(100)
    })

    it('ist monoton: mehr Ziehen => groesserer Indikator', () => {
      expect(dampPull(200)).toBeGreaterThan(dampPull(100))
    })
  })

  describe('shouldTriggerRefresh', () => {
    it('loest unterhalb der Schwelle NICHT aus', () => {
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX - 1)).toBe(false)
    })

    it('loest ab der Schwelle aus', () => {
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX)).toBe(true)
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX + 50)).toBe(true)
    })
  })
})

import { describe, it, expect } from 'vitest'
import { isInEdgeZone, shouldTriggerBack, EDGE_ZONE_PX, SWIPE_THRESHOLD_PX } from '../useSwipeBack'

describe('useSwipeBack - reine Gesten-Logik', () => {
  describe('isInEdgeZone', () => {
    it('akzeptiert Start direkt am linken Rand', () => {
      expect(isInEdgeZone(0)).toBe(true)
    })

    it('akzeptiert Start exakt an der Zonengrenze', () => {
      expect(isInEdgeZone(EDGE_ZONE_PX)).toBe(true)
    })

    it('lehnt Start ausserhalb der Randzone ab (Mitte = Carousel-Schutz)', () => {
      expect(isInEdgeZone(EDGE_ZONE_PX + 1)).toBe(false)
      expect(isInEdgeZone(200)).toBe(false)
    })

    it('lehnt negative (ungueltige) X-Position ab', () => {
      expect(isInEdgeZone(-5)).toBe(false)
    })
  })

  describe('shouldTriggerBack', () => {
    it('loest bei weitem, horizontal dominantem Rechts-Wisch aus', () => {
      expect(shouldTriggerBack(SWIPE_THRESHOLD_PX, 10)).toBe(true)
      expect(shouldTriggerBack(SWIPE_THRESHOLD_PX + 60, 20)).toBe(true)
    })

    it('loest unterhalb der Schwelle NICHT aus', () => {
      expect(shouldTriggerBack(SWIPE_THRESHOLD_PX - 1, 0)).toBe(false)
    })

    it('loest bei Links-Wisch NICHT aus', () => {
      expect(shouldTriggerBack(-SWIPE_THRESHOLD_PX, 0)).toBe(false)
    })

    it('loest bei ueberwiegend vertikaler Bewegung NICHT aus (= Scrollen)', () => {
      expect(shouldTriggerBack(SWIPE_THRESHOLD_PX, SWIPE_THRESHOLD_PX + 40)).toBe(false)
    })
  })
})

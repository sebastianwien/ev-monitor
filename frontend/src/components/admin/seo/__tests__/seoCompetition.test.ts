import { describe, it, expect } from 'vitest'
import { safeHref, usd, visibilityShare } from '../seoCompetition'

describe('seoCompetition', () => {
    it('rechnet Impressionen des Zeitraums auf einen Monat hoch und teilt durch das Suchvolumen', () => {
        // 280 Impressionen in 28 Tagen = 300 im Monat, bei 1 000 Suchen 30 %
        expect(visibilityShare(280, 28, 1000)).toBeCloseTo(0.3)
        expect(visibilityShare(5000, 28, 1000)).toBe(1)
        expect(visibilityShare(10, 28, null)).toBeNull()
        expect(visibilityShare(10, 28, 0)).toBeNull()
    })

    it('zeigt Dollarbeträge deutsch mit Währung', () => {
        expect(usd(0.84)).toBe('0,84 US$')
        expect(usd(null)).toBe('-')
    })

    it('lässt nur http(s)-Links aus fremden Suchergebnissen durch', () => {
        expect(safeHref('https://a.test/x')).toBe('https://a.test/x')
        expect(safeHref('javascript:alert(1)')).toBeUndefined()
        expect(safeHref(null)).toBeUndefined()
    })
})

import { describe, it, expect } from 'vitest'
import { getPricing } from '../pricingConfig'

describe('getPricing', () => {
    it('DE bekommt 3,90 EUR', () => {
        const p = getPricing('DE')
        expect(p.monthly).toBe('3,90 €')
        expect(p.yearly).toBe('39 €')
        expect(p.currency).toBe('EUR')
    })

    it('AT und CH bekommen denselben Preis wie DE', () => {
        expect(getPricing('AT').monthly).toBe('3,90 €')
        expect(getPricing('CH').monthly).toBe('3,90 €')
        expect(getPricing('AT').yearly).toBe('39 €')
        expect(getPricing('CH').yearly).toBe('39 €')
    })

    it('NL und BE bekommen 4,90 EUR (intl)', () => {
        expect(getPricing('NL').monthly).toBe('€4.90')
        expect(getPricing('BE').monthly).toBe('€4.90')
        expect(getPricing('NL').yearly).toBe('€49')
        expect(getPricing('BE').yearly).toBe('€49')
    })

    it('US bekommt USD-Preis', () => {
        const p = getPricing('US')
        expect(p.monthly).toBe('$5.49')
        expect(p.yearly).toBe('$54')
        expect(p.currency).toBe('USD')
    })

    it('GB bekommt GBP-Preis', () => {
        const p = getPricing('GB')
        expect(p.monthly).toBe('£4.49')
        expect(p.yearly).toBe('£44')
        expect(p.currency).toBe('GBP')
    })

    it('NO bekommt NOK-Preis', () => {
        const p = getPricing('NO')
        expect(p.monthly).toBe('69 kr')
        expect(p.yearly).toBe('690 kr')
        expect(p.currency).toBe('NOK')
    })

    it('SE bekommt SEK-Preis', () => {
        const p = getPricing('SE')
        expect(p.monthly).toBe('59 kr')
        expect(p.yearly).toBe('590 kr')
        expect(p.currency).toBe('SEK')
    })

    it('DK bekommt DKK-Preis mit englischem Savings-Text', () => {
        const p = getPricing('DK')
        expect(p.monthly).toBe('39 kr')
        expect(p.yearly).toBe('390 kr')
        expect(p.currency).toBe('DKK')
        expect(p.yearlySavings).toBe('2 months free')
    })

    it('unbekannte Countries bekommen intl EUR-Preis', () => {
        expect(getPricing('FR').monthly).toBe('€4.90')
        expect(getPricing('JP').monthly).toBe('€4.90')
        expect(getPricing('XX').currency).toBe('EUR')
    })
})

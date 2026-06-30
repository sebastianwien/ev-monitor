import { describe, it, expect } from 'vitest'
import { resolveLocalized } from '../surveys'

describe('resolveLocalized', () => {
    it('returns a plain string unchanged for any locale', () => {
        expect(resolveLocalized('Manuell', 'de')).toBe('Manuell')
        expect(resolveLocalized('Manuell', 'en')).toBe('Manuell')
        expect(resolveLocalized('Manuell', 'nb')).toBe('Manuell')
    })

    it('returns the German variant for de', () => {
        expect(resolveLocalized({ de: 'Sehr zufrieden', en: 'Very satisfied' }, 'de')).toBe('Sehr zufrieden')
    })

    it('returns the English variant for en', () => {
        expect(resolveLocalized({ de: 'Sehr zufrieden', en: 'Very satisfied' }, 'en')).toBe('Very satisfied')
    })

    it('falls back to English for nb and sv (no Norwegian/Swedish authored)', () => {
        const v = { de: 'Sehr zufrieden', en: 'Very satisfied' }
        expect(resolveLocalized(v, 'nb')).toBe('Very satisfied')
        expect(resolveLocalized(v, 'sv')).toBe('Very satisfied')
    })
})

import { describe, it, expect, afterEach } from 'vitest'
import { isValidLocale, getSavedLocale } from '../i18n'

describe('isValidLocale', () => {
    it('accepts de', () => expect(isValidLocale('de')).toBe(true))
    it('accepts en', () => expect(isValidLocale('en')).toBe(true))
    it('accepts nb', () => expect(isValidLocale('nb')).toBe(true))
    it('accepts sv', () => expect(isValidLocale('sv')).toBe(true))
    it('rejects fr', () => expect(isValidLocale('fr')).toBe(false))
    it('rejects empty string', () => expect(isValidLocale('')).toBe(false))
    it('rejects null', () => expect(isValidLocale(null)).toBe(false))
    it('rejects undefined', () => expect(isValidLocale(undefined)).toBe(false))
    it('rejects number', () => expect(isValidLocale(42)).toBe(false))
})

describe('getSavedLocale', () => {
    afterEach(() => localStorage.removeItem('ev-locale'))

    it('returns de as default when nothing saved', () => {
        expect(getSavedLocale()).toBe('de')
    })

    it('returns en when saved', () => {
        localStorage.setItem('ev-locale', 'en')
        expect(getSavedLocale()).toBe('en')
    })

    it('returns nb when saved', () => {
        localStorage.setItem('ev-locale', 'nb')
        expect(getSavedLocale()).toBe('nb')
    })

    it('returns sv when saved', () => {
        localStorage.setItem('ev-locale', 'sv')
        expect(getSavedLocale()).toBe('sv')
    })

    it('falls back to de for unsupported locale', () => {
        localStorage.setItem('ev-locale', 'fr')
        expect(getSavedLocale()).toBe('de')
    })

    it('falls back to de for empty string', () => {
        localStorage.setItem('ev-locale', '')
        expect(getSavedLocale()).toBe('de')
    })
})

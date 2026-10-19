import { describe, it, expect } from 'vitest'
import { buildUtmLink, slugUtm } from '../seoUtm'

describe('seoUtm', () => {
    it('normalizes values: lowercase, umlauts, spaces and symbols to dashes', () => {
        expect(slugUtm('  VW-Forum Ärger & Co ')).toBe('vw-forum-aerger-co')
        expect(slugUtm('Herbst_2026')).toBe('herbst_2026')
    })

    it('appends UTM parameters and keeps existing query and fragment', () => {
        expect(buildUtmLink({ url: 'https://ev-monitor.net/modelle/Tesla/Model_3', source: 'VW Forum', medium: 'forum', campaign: 'Herbst 2026' }))
            .toBe('https://ev-monitor.net/modelle/Tesla/Model_3?utm_source=vw-forum&utm_medium=forum&utm_campaign=herbst-2026')
        expect(buildUtmLink({ url: 'https://ev-monitor.net/?a=1#faq', source: 'x', medium: 'email', campaign: '' }))
            .toBe('https://ev-monitor.net/?a=1&utm_source=x&utm_medium=email#faq')
    })

    it('returns null without source or with an invalid or foreign URL', () => {
        expect(buildUtmLink({ url: 'https://ev-monitor.net/', source: '', medium: 'forum', campaign: 'a' })).toBeNull()
        expect(buildUtmLink({ url: 'kein link', source: 'a', medium: 'forum', campaign: '' })).toBeNull()
        expect(buildUtmLink({ url: 'https://example.com/', source: 'a', medium: 'forum', campaign: '' })).toBeNull()
    })
})

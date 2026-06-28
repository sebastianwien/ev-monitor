import { describe, it, expect } from 'vitest'
import { isCrawler } from '../isCrawler'

describe('isCrawler', () => {
    it('detects the prerender service via navigator.webdriver', () => {
        // The prerender service runs CDP-controlled headless Chrome, which sets
        // navigator.webdriver = true. This is the signal that must suppress the
        // browser-language redirect so German SEO URLs keep their German content.
        expect(isCrawler({ userAgent: 'Mozilla/5.0 ... Chrome/120', webdriver: true })).toBe(true)
    })

    it('detects Googlebot by user agent', () => {
        expect(isCrawler({
            userAgent: 'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
            webdriver: false,
        })).toBe(true)
    })

    it('detects the prerender token in the user agent', () => {
        expect(isCrawler({
            userAgent: 'Mozilla/5.0 ... Prerender (+https://github.com/prerender/prerender)',
            webdriver: false,
        })).toBe(true)
    })

    it.each([
        'bingbot', 'yandexbot', 'duckduckbot', 'applebot', 'ahrefsbot',
        'semrushbot', 'gptbot', 'claudebot', 'perplexitybot', 'facebookexternalhit',
    ])('detects crawler "%s" by user agent', (token) => {
        expect(isCrawler({ userAgent: `Mozilla/5.0 (compatible; ${token}/1.0)`, webdriver: false })).toBe(true)
    })

    it('returns false for a real German desktop browser', () => {
        expect(isCrawler({
            userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36',
            webdriver: false,
        })).toBe(false)
    })

    it('returns false for a real mobile browser', () => {
        expect(isCrawler({
            userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
            webdriver: false,
        })).toBe(false)
    })

    it('treats undefined navigator fields as a real user', () => {
        expect(isCrawler({})).toBe(false)
    })
})

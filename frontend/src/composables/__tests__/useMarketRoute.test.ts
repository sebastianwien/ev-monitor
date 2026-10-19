import { describe, it, expect } from 'vitest'
import { detectMarket, getMarketBasePath, buildMarketUrl, robotsFor, robotsForPath } from '../useMarketRoute'

const BASE = 'https://ev-monitor.net'

describe('detectMarket', () => {
    it('erkennt DE für /modelle', () => {
        expect(detectMarket('/modelle')).toBe('de')
    })

    it('erkennt DE für /modelle/Tesla', () => {
        expect(detectMarket('/modelle/Tesla')).toBe('de')
    })

    it('erkennt DE für /modelle/Tesla/Model_3', () => {
        expect(detectMarket('/modelle/Tesla/Model_3')).toBe('de')
    })

    it('erkennt EN für /en/models', () => {
        expect(detectMarket('/en/models')).toBe('en')
    })

    it('erkennt EN für /en/models/Tesla/Model_3', () => {
        expect(detectMarket('/en/models/Tesla/Model_3')).toBe('en')
    })

    it('erkennt US für /us/models', () => {
        expect(detectMarket('/us/models')).toBe('us')
    })

    it('erkennt US für /us/models/Tesla/Model_3', () => {
        expect(detectMarket('/us/models/Tesla/Model_3')).toBe('us')
    })

    it('erkennt NO für /no/modeller', () => {
        expect(detectMarket('/no/modeller')).toBe('no')
    })

    it('erkennt NO für /no/modeller/Tesla/Model_3', () => {
        expect(detectMarket('/no/modeller/Tesla/Model_3')).toBe('no')
    })

    it('erkennt SE für /se/modeller', () => {
        expect(detectMarket('/se/modeller')).toBe('se')
    })

    it('erkennt SE für /se/modeller/Tesla/Model_3', () => {
        expect(detectMarket('/se/modeller/Tesla/Model_3')).toBe('se')
    })

    it('erkennt GB für /gb/models', () => {
        expect(detectMarket('/gb/models')).toBe('gb')
    })

    it('erkennt GB für /gb/models/Tesla/Model_3', () => {
        expect(detectMarket('/gb/models/Tesla/Model_3')).toBe('gb')
    })

    it('fällt auf de zurück bei unbekanntem Pfad', () => {
        expect(detectMarket('/dashboard')).toBe('de')
    })
})

describe('getMarketBasePath', () => {
    it('gibt /modelle für de zurück', () => {
        expect(getMarketBasePath('de')).toBe('/modelle')
    })

    it('gibt /en/models für en zurück', () => {
        expect(getMarketBasePath('en')).toBe('/en/models')
    })

    it('gibt /us/models für us zurück', () => {
        expect(getMarketBasePath('us')).toBe('/us/models')
    })

    it('gibt /no/modeller für no zurück', () => {
        expect(getMarketBasePath('no')).toBe('/no/modeller')
    })

    it('gibt /se/modeller für se zurück', () => {
        expect(getMarketBasePath('se')).toBe('/se/modeller')
    })

    it('gibt /gb/models für gb zurück', () => {
        expect(getMarketBasePath('gb')).toBe('/gb/models')
    })
})

describe('buildMarketUrl', () => {
    it('baut DE List-URL', () => {
        expect(buildMarketUrl('de')).toBe(`${BASE}/modelle`)
    })

    it('baut US Brand-URL', () => {
        expect(buildMarketUrl('us', '/Tesla')).toBe(`${BASE}/us/models/Tesla`)
    })

    it('baut NO Modell-URL', () => {
        expect(buildMarketUrl('no', '/Tesla/Model_3')).toBe(`${BASE}/no/modeller/Tesla/Model_3`)
    })

    it('baut SE Modell-URL', () => {
        expect(buildMarketUrl('se', '/Tesla/Model_3')).toBe(`${BASE}/se/modeller/Tesla/Model_3`)
    })

    it('baut EN Modell-URL', () => {
        expect(buildMarketUrl('en', '/Tesla/Model_3')).toBe(`${BASE}/en/models/Tesla/Model_3`)
    })

    it('baut GB Modell-URL', () => {
        expect(buildMarketUrl('gb', '/Tesla/Model_3')).toBe(`${BASE}/gb/models/Tesla/Model_3`)
    })
})

describe('robotsFor', () => {
    it('DE ist die einzige indexierte Sprache', () => {
        expect(robotsFor('de')).toBe('index, follow')
    })

    it.each(['en', 'gb', 'us', 'no', 'se'] as const)('%s bekommt noindex, follow', market => {
        expect(robotsFor(market)).toBe('noindex, follow')
    })
})

describe('robotsForPath (globales Sicherheitsnetz in App.vue)', () => {
    it.each(['/en', '/en/login', '/en/register', '/gb/models', '/no/modeller/Tesla', '/se/modeller'])('%s ist noindex', path => {
        expect(robotsForPath(path)).toBe('noindex, follow')
    })

    it.each(['/', '/modelle', '/modelle/Tesla/Model_3', '/login', '/english-cars', '/settings'])('%s bleibt index', path => {
        expect(robotsForPath(path)).toBe('index, follow')
    })
})

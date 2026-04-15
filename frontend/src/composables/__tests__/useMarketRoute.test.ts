import { describe, it, expect } from 'vitest'
import { detectMarket, getMarketBasePath, buildMarketUrl, getHreflangLinks } from '../useMarketRoute'

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

describe('getHreflangLinks', () => {
    it('gibt 7 Links zurück (6 Märkte + x-default)', () => {
        const links = getHreflangLinks()
        expect(links).toHaveLength(7)
    })

    it('enthält hreflang="de"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const de = links.find(l => l.hreflang === 'de')
        expect(de?.href).toBe(`${BASE}/modelle/Tesla/Model_3`)
    })

    it('enthält hreflang="en"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const en = links.find(l => l.hreflang === 'en')
        expect(en?.href).toBe(`${BASE}/en/models/Tesla/Model_3`)
    })

    it('enthält hreflang="en-US"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const enUs = links.find(l => l.hreflang === 'en-US')
        expect(enUs?.href).toBe(`${BASE}/us/models/Tesla/Model_3`)
    })

    it('enthält hreflang="nb"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const nb = links.find(l => l.hreflang === 'nb')
        expect(nb?.href).toBe(`${BASE}/no/modeller/Tesla/Model_3`)
    })

    it('enthält hreflang="sv"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const sv = links.find(l => l.hreflang === 'sv')
        expect(sv?.href).toBe(`${BASE}/se/modeller/Tesla/Model_3`)
    })

    it('enthält hreflang="en-GB"', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const enGb = links.find(l => l.hreflang === 'en-GB')
        expect(enGb?.href).toBe(`${BASE}/gb/models/Tesla/Model_3`)
    })

    it('x-default zeigt auf /en/models', () => {
        const links = getHreflangLinks('/Tesla/Model_3')
        const xDefault = links.find(l => l.hreflang === 'x-default')
        expect(xDefault?.href).toBe(`${BASE}/en/models/Tesla/Model_3`)
    })

    it('funktioniert ohne suffix (List-Seiten)', () => {
        const links = getHreflangLinks()
        const de = links.find(l => l.hreflang === 'de')
        expect(de?.href).toBe(`${BASE}/modelle`)
    })
})

import { describe, it, expect } from 'vitest'
import { buildProductJsonLd } from '../ratingJsonLd'
import type { PublicModelRatings } from '../../api/ratingService'

const base = { name: 'Volkswagen ID.3', brand: 'Volkswagen', url: 'https://ev-monitor.net/modelle/Volkswagen/ID.3', driverLabel: 'ID.3-Fahrer' }

function ratings(over: Partial<PublicModelRatings> = {}): PublicModelRatings {
    return {
        count: 0, rebuy: { yes: 0, brandOtherModel: 0, no: 0 }, rebuyReasons: [], categories: null, totalCost: null,
        strengths: [], weaknesses: [], singles: [], quotes: [], reports: [], aggregate: null,
        ...over,
    }
}

const single = (ratingValue: number, withReport = false) => ({
    rebuy: 'YES' as const, dailyFit: 4, longDistance: 4, software: 5, buildQuality: 4, service: null,
    totalCost: 'EXPECTED' as const, kmRounded: 25_000, businessCar: false, ratingValue, withReport,
})

const report = (i: number) => ({
    text: `Bericht ${i}`, pros: [], cons: [], kmRounded: 25_000, month: '2026-10', logCount: 40,
    consumptionKwhPer100km: 16.8, ratingValue: 4.2, publishedOn: `2026-10-0${i}`,
})

describe('Product-JSON-LD der Modellseite', () => {
    it('gibt ohne Bewertungen kein Product aus', () => {
        expect(buildProductJsonLd({ ...base, ratings: null })).toBeNull()
        expect(buildProductJsonLd({ ...base, ratings: ratings({ count: 0 }) })).toBeNull()
    })

    it('zeigt aggregateRating erst ab 5 Bewertungen', () => {
        const ld = buildProductJsonLd({ ...base, ratings: ratings({ count: 5, aggregate: { ratingValue: 3.84, ratingCount: 5 } }) })!

        expect(ld['@type']).toBe('Product')
        expect(ld.brand).toEqual({ '@type': 'Brand', name: 'Volkswagen' })
        expect(ld.aggregateRating).toEqual({
            '@type': 'AggregateRating', ratingValue: 3.8, bestRating: 5, worstRating: 1, ratingCount: 5,
        })
        expect(ld.review).toBeUndefined()
    })

    it('trägt ohne aggregateRating höchstens eine Review, sonst verwirft Google das Product', () => {
        const ld = buildProductJsonLd({ ...base, ratings: ratings({ count: 4, reports: [1, 2, 3, 4, 5, 6].map(report) }) })!

        expect(ld.aggregateRating).toBeUndefined()
        const reviews = ld.review as Record<string, unknown>[]
        expect(reviews).toHaveLength(1)
        expect(reviews[0]).toEqual({
            '@type': 'Review',
            reviewRating: { '@type': 'Rating', ratingValue: 4.2, bestRating: 5, worstRating: 1 },
            author: { '@type': 'Person', name: 'ID.3-Fahrer' },
            reviewBody: 'Bericht 1',
            datePublished: '2026-10-01',
        })
    })

    it('nimmt ohne aggregateRating zuerst den Bericht, ohne Bericht die erste Einzelbewertung', () => {
        const withReport = buildProductJsonLd({ ...base, ratings: ratings({ count: 2, singles: [single(4.3), single(3.8, true)], reports: [report(1)] }) })!
        expect(withReport.aggregateRating).toBeUndefined()
        expect(withReport.review as unknown[]).toHaveLength(1)
        expect((withReport.review as Record<string, unknown>[])[0].reviewBody).toBe('Bericht 1')

        const ld = buildProductJsonLd({ ...base, ratings: ratings({ count: 2, singles: [single(4.3), single(3.8)] }) })!
        const reviews = ld.review as Record<string, unknown>[]
        expect(reviews).toHaveLength(1)
        expect(reviews[0]).toEqual({
            '@type': 'Review',
            reviewRating: { '@type': 'Rating', ratingValue: 4.3, bestRating: 5, worstRating: 1 },
            author: { '@type': 'Person', name: 'ID.3-Fahrer' },
        })
    })

    it('trägt mit aggregateRating bis zu 5 Reviews', () => {
        const ld = buildProductJsonLd({ ...base, ratings: ratings({ count: 8, aggregate: { ratingValue: 4, ratingCount: 8 }, reports: [1, 2, 3, 4, 5, 6].map(report) }) })!
        expect(ld.review as unknown[]).toHaveLength(5)
    })

    it('setzt Product schon bei einer einzigen Bewertung ohne Text', () => {
        const ld = buildProductJsonLd({ ...base, ratings: ratings({ count: 1, singles: [single(2.6)] }) })!
        expect((ld.review as unknown[])).toHaveLength(1)
    })
})

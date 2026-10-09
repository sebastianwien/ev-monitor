import type { PublicModelRatings } from '../api/ratingService'

const MAX_REVIEWS = 5

/**
 * Product-Objekt als Träger für Fahrerbewertungen. Nur mit aggregateRating (ab 5 Bewertungen) oder
 * mindestens einem Erfahrungsbericht - ein Product ohne beides löst in der Search Console eine Warnung aus.
 */
export function buildProductJsonLd(input: {
    name: string
    brand: string
    url: string
    driverLabel: string
    ratings: PublicModelRatings | null
}): Record<string, unknown> | null {
    const { ratings } = input
    if (!ratings) return null
    const reviews = ratings.reports.slice(0, MAX_REVIEWS)
    if (!ratings.aggregate && reviews.length === 0) return null

    const product: Record<string, unknown> = {
        '@context': 'https://schema.org',
        '@type': 'Product',
        name: input.name,
        brand: { '@type': 'Brand', name: input.brand },
        url: input.url,
    }
    if (ratings.aggregate) {
        product.aggregateRating = {
            '@type': 'AggregateRating',
            ratingValue: Math.round(ratings.aggregate.ratingValue * 10) / 10,
            bestRating: 5,
            worstRating: 1,
            ratingCount: ratings.aggregate.ratingCount,
        }
    }
    if (reviews.length > 0) {
        product.review = reviews.map(r => ({
            '@type': 'Review',
            reviewRating: { '@type': 'Rating', ratingValue: r.ratingValue, bestRating: 5, worstRating: 1 },
            author: { '@type': 'Person', name: input.driverLabel },
            reviewBody: r.text,
            datePublished: r.publishedOn,
        }))
    }
    return product
}

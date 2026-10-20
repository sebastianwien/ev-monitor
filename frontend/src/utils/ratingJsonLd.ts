import type { PublicModelRatings } from '../api/ratingService'

const MAX_REVIEWS = 5

/**
 * Product-Objekt als Träger für Fahrerbewertungen. Ab 5 Bewertungen mit aggregateRating, darunter jede Einzelbewertung
 * als Review mit ihrer Gesamtnote. Erfahrungsberichte stehen immer als Review mit Text drin. Alles, was hier steht, ist
 * auf der Seite sichtbar (Googles Regel für strukturierte Daten). Ohne Bewertung kein Product: Google verwirft eins
 * ohne review, aggregateRating oder offers.
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
    const reviews: Record<string, unknown>[] = ratings.reports.map(r => ({
        '@type': 'Review',
        reviewRating: { '@type': 'Rating', ratingValue: r.ratingValue, bestRating: 5, worstRating: 1 },
        author: { '@type': 'Person', name: input.driverLabel },
        reviewBody: r.text,
        datePublished: r.publishedOn,
    }))
    // Einzelkarten gibt es nur unter 5 Bewertungen; eine Karte mit Bericht steht oben schon mit Text
    for (const s of ratings.singles) {
        if (s.withReport) continue
        reviews.push({
            '@type': 'Review',
            reviewRating: { '@type': 'Rating', ratingValue: s.ratingValue, bestRating: 5, worstRating: 1 },
            author: { '@type': 'Person', name: input.driverLabel },
        })
    }
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
    if (reviews.length > 0) product.review = reviews.slice(0, MAX_REVIEWS)
    return product
}

import type { RatingChip, RebuyAnswer, RebuyReason, TotalCostVerdict } from '../api/ratingService'

/** Sterne-Fragen in Anzeigereihenfolge. Texte unter rating.q.<key> in den Locales. */
export const STAR_QUESTIONS = [
    { key: 'daily_fit', field: 'dailyFit', skippable: false },
    { key: 'long_distance', field: 'longDistance', skippable: true },
    { key: 'software', field: 'software', skippable: false },
    { key: 'build_quality', field: 'buildQuality', skippable: false },
    { key: 'service', field: 'service', skippable: true },
] as const

export const REBUY_ANSWERS: RebuyAnswer[] = ['YES', 'BRAND_OTHER_MODEL', 'NO']
export const REBUY_REASONS: RebuyReason[] = ['SIZE', 'RANGE', 'PRICE', 'EQUIPMENT', 'SOFTWARE']
export const TOTAL_COST_VERDICTS: TotalCostVerdict[] = ['CHEAPER', 'EXPECTED', 'PRICIER']
export const RATING_CHIPS: RatingChip[] = [
    'EFFICIENCY', 'CHARGING_SPEED', 'SPACE', 'DRIVING_FUN', 'PRICE',
    'SOFTWARE', 'BUILD_QUALITY', 'DESIGN', 'ASSISTANCE', 'SERVICE',
]

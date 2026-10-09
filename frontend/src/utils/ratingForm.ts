import type {
    MyRating,
    RatingAnswers,
    RatingChip,
    RebuyAnswer,
    RebuyReason,
    TotalCostVerdict,
} from '../api/ratingService'

/** Regeln des Bewertungsformulars. Die Grenzen spiegeln die Serverprüfung, der Server bleibt maßgeblich. */
export const CORE_QUESTION_COUNT = 7
export const MAX_CHIPS = 3
export const REPORT_MIN_CHARS = 600
export const MAX_BODY_CHARS = 5000
export const MAX_LIST_ITEMS = 5
export const MAX_ITEM_CHARS = 120
/** Ab so vielen Bewertungen zeigt der Server Durchschnitte (RatingAggregator.MIN_FOR_AVERAGE). */
export const MIN_FOR_AVERAGE = 5

/** "Fahre ich selten Langstrecke" bzw. "Noch nicht gebraucht": beantwortet, aber ohne Sterne. */
export const SKIP = 'SKIP' as const
type Stars = number | null
type OptionalStars = number | typeof SKIP | null

export interface RatingDraft {
    rebuy: RebuyAnswer | null
    rebuyReasons: RebuyReason[]
    dailyFit: Stars
    longDistance: OptionalStars
    software: Stars
    buildQuality: Stars
    service: OptionalStars
    totalCost: TotalCostVerdict | null
    strengths: RatingChip[]
    weaknesses: RatingChip[]
    body: string
    pros: string[]
    cons: string[]
    deleteWithCar: boolean
}

export function emptyDraft(): RatingDraft {
    return {
        rebuy: null,
        rebuyReasons: [],
        dailyFit: null,
        longDistance: null,
        software: null,
        buildQuality: null,
        service: null,
        totalCost: null,
        strengths: [],
        weaknesses: [],
        body: '',
        pros: [],
        cons: [],
        deleteWithCar: false,
    }
}

export function answeredCount(d: RatingDraft): number {
    return [d.rebuy, d.dailyFit, d.longDistance, d.software, d.buildQuality, d.service, d.totalCost]
        .filter(v => v !== null).length
}

export function canSubmitCore(d: RatingDraft): boolean {
    return answeredCount(d) === CORE_QUESTION_COUNT
}

/** Warum-Chips gehören nur zu "Marke ja, Modell nein" - beim Wechsel fallen sie weg. */
export function setRebuy(d: RatingDraft, answer: RebuyAnswer): void {
    d.rebuy = answer
    if (answer !== 'BRAND_OTHER_MODEL') d.rebuyReasons = []
}

export function toggleChip<T extends string>(list: T[], chip: T, max = MAX_CHIPS): T[] {
    if (list.includes(chip)) return list.filter(c => c !== chip)
    return list.length >= max ? list : [...list, chip]
}

export function isChipDisabled<T extends string>(list: T[], chip: T, max = MAX_CHIPS): boolean {
    return !list.includes(chip) && list.length >= max
}

function stars(value: OptionalStars): number | null {
    return value === SKIP ? null : value
}

/** @throws Error, wenn eine Kernfrage fehlt */
export function toAnswers(d: RatingDraft): RatingAnswers {
    if (!canSubmitCore(d)) throw new Error('Bewertung unvollständig')
    return {
        rebuy: d.rebuy!,
        rebuyReasons: d.rebuy === 'BRAND_OTHER_MODEL' ? [...d.rebuyReasons] : [],
        dailyFit: d.dailyFit!,
        longDistance: stars(d.longDistance),
        software: d.software!,
        buildQuality: d.buildQuality!,
        service: stars(d.service),
        totalCost: d.totalCost!,
        strengths: [...d.strengths],
        weaknesses: [...d.weaknesses],
    }
}

export function fromMyRating(my: MyRating): RatingDraft {
    const a = my.answers
    return {
        rebuy: a.rebuy,
        rebuyReasons: [...a.rebuyReasons],
        dailyFit: a.dailyFit,
        longDistance: a.longDistance ?? SKIP,
        software: a.software,
        buildQuality: a.buildQuality,
        service: a.service ?? SKIP,
        totalCost: a.totalCost,
        strengths: [...a.strengths],
        weaknesses: [...a.weaknesses],
        body: my.review?.body ?? '',
        pros: [...(my.review?.pros ?? [])],
        cons: [...(my.review?.cons ?? [])],
        deleteWithCar: my.review?.deleteWithCar ?? false,
    }
}

/** Wie der Text öffentlich erscheint: als Zitat oder ab 600 Zeichen als Erfahrungsbericht. */
export function reviewKind(body: string): 'none' | 'quote' | 'report' {
    const length = body.trim().length
    if (length === 0) return 'none'
    return length >= REPORT_MIN_CHARS ? 'report' : 'quote'
}

const draftKey = (carId: string) => `rating-draft:${carId}`

/** Entwurf im Browser, damit ein Abbruch nichts verliert. Speicherfehler sind egal. */
export const draftStorage = {
    load(carId: string): RatingDraft | null {
        try {
            const raw = localStorage.getItem(draftKey(carId))
            return raw ? { ...emptyDraft(), ...JSON.parse(raw) } : null
        } catch {
            return null
        }
    },
    save(carId: string, draft: RatingDraft): void {
        try {
            localStorage.setItem(draftKey(carId), JSON.stringify(draft))
        } catch {
            // Privater Modus oder voller Speicher: ohne Entwurf weiter
        }
    },
    clear(carId: string): void {
        try {
            localStorage.removeItem(draftKey(carId))
        } catch {
            // egal
        }
    },
}

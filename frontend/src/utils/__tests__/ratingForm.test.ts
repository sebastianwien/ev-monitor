import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
    emptyDraft,
    answeredCount,
    canSubmitCore,
    setRebuy,
    toggleChip,
    isChipDisabled,
    toAnswers,
    fromMyRating,
    reviewKind,
    draftStorage,
    CORE_QUESTION_COUNT,
    type RatingDraft,
} from '../ratingForm'
import type { MyRating, RatingChip } from '../../api/ratingService'

function complete(): RatingDraft {
    return {
        ...emptyDraft(),
        rebuy: 'YES',
        dailyFit: 4,
        longDistance: 'SKIP',
        software: 3,
        buildQuality: 5,
        service: 'SKIP',
        totalCost: 'EXPECTED',
    }
}

describe('Kernfragen', () => {
    it('zählt sieben Fragen, "selten Langstrecke" und "noch nicht gebraucht" gelten als beantwortet', () => {
        expect(CORE_QUESTION_COUNT).toBe(7)
        expect(answeredCount(emptyDraft())).toBe(0)
        expect(answeredCount(complete())).toBe(7)
    })

    it('sperrt das Absenden, bis alle sieben beantwortet sind', () => {
        const draft = complete()
        draft.totalCost = null
        expect(answeredCount(draft)).toBe(6)
        expect(canSubmitCore(draft)).toBe(false)
        expect(canSubmitCore(complete())).toBe(true)
    })

    it('übersetzt "selten" und "noch nicht gebraucht" in null', () => {
        const answers = toAnswers(complete())
        expect(answers.longDistance).toBeNull()
        expect(answers.service).toBeNull()
        expect(answers.dailyFit).toBe(4)
    })

    it('wirft bei unvollständigem Entwurf statt halbe Daten zu senden', () => {
        expect(() => toAnswers(emptyDraft())).toThrow()
    })
})

describe('Warum-Chips beim Wiederkauf', () => {
    it('bleiben nur bei "Marke ja, Modell nein" erhalten', () => {
        const draft = complete()
        setRebuy(draft, 'BRAND_OTHER_MODEL')
        draft.rebuyReasons = ['SIZE', 'RANGE']
        expect(toAnswers(draft).rebuyReasons).toEqual(['SIZE', 'RANGE'])

        setRebuy(draft, 'YES')
        expect(draft.rebuyReasons).toEqual([])
        expect(toAnswers(draft).rebuyReasons).toEqual([])
    })
})

describe('Stärken und Schwächen', () => {
    it('erlaubt höchstens drei Chips je Liste', () => {
        let list: RatingChip[] = toggleChip<RatingChip>([], 'SPACE')
        list = toggleChip(list, 'DESIGN')
        list = toggleChip(list, 'PRICE')
        expect(isChipDisabled(list, 'SOFTWARE')).toBe(true)
        expect(toggleChip(list, 'SOFTWARE')).toEqual(['SPACE', 'DESIGN', 'PRICE'])
        expect(isChipDisabled(list, 'SPACE')).toBe(false)
    })

    it('wählt einen gewählten Chip wieder ab', () => {
        expect(toggleChip<RatingChip>(['SPACE', 'DESIGN'], 'SPACE')).toEqual(['DESIGN'])
    })
})

describe('Erfahrungstext', () => {
    it('ist unter 600 Zeichen ein Zitat und ab 600 ein Bericht', () => {
        expect(reviewKind('   ')).toBe('none')
        expect(reviewKind('a'.repeat(599))).toBe('quote')
        expect(reviewKind('a'.repeat(600))).toBe('report')
        expect(reviewKind(`  ${'a'.repeat(599)}  `)).toBe('quote')
    })
})

describe('Bearbeiten', () => {
    it('füllt den Entwurf aus der gespeicherten Bewertung vor', () => {
        const my: MyRating = {
            answers: {
                rebuy: 'BRAND_OTHER_MODEL', rebuyReasons: ['PRICE'], dailyFit: 2, longDistance: null, software: 4,
                buildQuality: 3, service: 5, totalCost: 'PRICIER', strengths: ['SPACE'], weaknesses: [],
            },
            kmDriven: 12_000, logCount: 30, createdAt: '2026-10-01T10:00:00', updatedAt: '2026-10-01T10:00:00',
            review: {
                body: 'Text', pros: ['Platz'], cons: [], status: 'PENDING', rejectionReason: null, rejectionNote: null,
                published: false, deleteWithCar: true, submittedAt: '2026-10-01T10:00:00',
            },
        }

        const draft = fromMyRating(my)

        expect(draft.longDistance).toBe('SKIP')
        expect(draft.service).toBe(5)
        expect(draft.rebuyReasons).toEqual(['PRICE'])
        expect(draft.body).toBe('Text')
        expect(draft.deleteWithCar).toBe(true)
        expect(canSubmitCore(draft)).toBe(true)
    })
})

describe('Entwurfsspeicher', () => {
    beforeEach(() => {
        localStorage.clear()
        vi.restoreAllMocks()
    })

    it('legt den Entwurf je Auto ab und lädt ihn wieder', () => {
        draftStorage.save('car-1', complete())
        expect(draftStorage.load('car-1')?.dailyFit).toBe(4)
        expect(draftStorage.load('car-2')).toBeNull()

        draftStorage.clear('car-1')
        expect(draftStorage.load('car-1')).toBeNull()
    })

    it('übersteht gesperrten oder kaputten Speicher', () => {
        localStorage.setItem('rating-draft:car-1', '{kaputt')
        expect(draftStorage.load('car-1')).toBeNull()

        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('QuotaExceeded') })
        expect(() => draftStorage.save('car-1', complete())).not.toThrow()
    })
})

import { describe, it, expect } from 'vitest'
import {
    resolveLocalized,
    isQuestionAnswered,
    isPageComplete,
    surveys,
    MAX_TEXT_ANSWER_LENGTH,
    type SurveyPage,
} from '../surveys'

describe('resolveLocalized', () => {
    it('returns a plain string unchanged for any locale', () => {
        expect(resolveLocalized('Manuell', 'de')).toBe('Manuell')
        expect(resolveLocalized('Manuell', 'en')).toBe('Manuell')
        expect(resolveLocalized('Manuell', 'nb')).toBe('Manuell')
    })

    it('returns the German variant for de', () => {
        expect(resolveLocalized({ de: 'Sehr zufrieden', en: 'Very satisfied' }, 'de')).toBe('Sehr zufrieden')
    })

    it('returns the English variant for en', () => {
        expect(resolveLocalized({ de: 'Sehr zufrieden', en: 'Very satisfied' }, 'en')).toBe('Very satisfied')
    })

    it('falls back to English for nb and sv (no Norwegian/Swedish authored)', () => {
        const v = { de: 'Sehr zufrieden', en: 'Very satisfied' }
        expect(resolveLocalized(v, 'nb')).toBe('Very satisfied')
        expect(resolveLocalized(v, 'sv')).toBe('Very satisfied')
    })
})

describe('isQuestionAnswered', () => {
    const single = { key: 'q', label: 'l', options: [{ value: 'a', label: 'A' }] }
    const multi = { ...single, multiple: true }
    const text = { key: 'q', label: 'l', type: 'text' } as const

    it('treats a picked radio option as answered', () => {
        expect(isQuestionAnswered(single, { q: 'a' })).toBe(true)
        expect(isQuestionAnswered(single, {})).toBe(false)
        expect(isQuestionAnswered(single, { q: '' })).toBe(false)
    })

    it('requires at least one checkbox for multiple choice', () => {
        expect(isQuestionAnswered(multi, { q: ['a'] })).toBe(true)
        expect(isQuestionAnswered(multi, { q: [] })).toBe(false)
        expect(isQuestionAnswered(multi, {})).toBe(false)
    })

    it('requires non-whitespace input for text questions', () => {
        expect(isQuestionAnswered(text, { q: 'Ladesaeule war belegt' })).toBe(true)
        expect(isQuestionAnswered(text, { q: '   ' })).toBe(false)
        expect(isQuestionAnswered(text, {})).toBe(false)
    })

    it('always counts optional questions as answered', () => {
        expect(isQuestionAnswered({ ...text, optional: true }, {})).toBe(true)
        expect(isQuestionAnswered({ ...single, optional: true }, {})).toBe(true)
    })
})

describe('isPageComplete', () => {
    const page: SurveyPage = {
        questions: [
            { key: 'a', label: 'A', type: 'text' },
            { key: 'b', label: 'B', type: 'text', optional: true },
        ],
    }

    it('is complete once every required question has an answer', () => {
        expect(isPageComplete(page, {})).toBe(false)
        expect(isPageComplete(page, { a: 'x' })).toBe(true)
    })

    it('is complete for a page without required questions', () => {
        expect(isPageComplete({ questions: [{ key: 'b', label: 'B', type: 'text', optional: true }] }, {})).toBe(true)
    })
})

describe('survey configs', () => {
    it('exposes every survey under its own slug with at least one page', () => {
        for (const [slug, survey] of Object.entries(surveys)) {
            expect(survey.slug).toBe(slug)
            expect(survey.pages.length).toBeGreaterThan(0)
            for (const page of survey.pages) {
                expect(page.questions.length).toBeGreaterThan(0)
            }
        }
    })

    it('uses unique question keys per survey', () => {
        for (const survey of Object.values(surveys)) {
            const keys = survey.pages.flatMap(p => p.questions.map(q => q.key))
            expect(new Set(keys).size).toBe(keys.length)
        }
    })

    it('splits the EV pain points survey into pain points and optional demographics', () => {
        const survey = surveys['ev-pain-points-2026']
        expect(survey.pages).toHaveLength(2)
        expect(survey.pages[0].questions.every(q => !q.optional)).toBe(true)
        expect(survey.pages[1].questions.every(q => q.optional)).toBe(true)
    })

    it('keeps text answers within the backend limit', () => {
        for (const survey of Object.values(surveys)) {
            for (const page of survey.pages) {
                for (const q of page.questions) {
                    if (q.type === 'text') {
                        expect(q.maxLength ?? MAX_TEXT_ANSWER_LENGTH).toBeLessThanOrEqual(MAX_TEXT_ANSWER_LENGTH)
                    }
                }
            }
        }
    })
})

import { describe, it, expect } from 'vitest'
import { isRedactionOf, highlightSegments } from '../../api/adminRatingService'

describe('Schwärzungsregel im Admin-Tab', () => {
    const body = 'Händler Meier (K-AB 123) war super.'
    it('erlaubt nur Ersetzen durch [entfernt]', () => {
        expect(isRedactionOf(body, body)).toBe(true)
        expect(isRedactionOf(body, 'Händler [entfernt] ([entfernt]) war super.')).toBe(true)
        expect(isRedactionOf(body, 'Händler Meier war super.')).toBe(false)
        expect(isRedactionOf('abc', 'ab[entfernt]c')).toBe(false)
    })
})

describe('Fundstellen markieren', () => {
    it('zerlegt den Text an den Zitaten', () => {
        expect(highlightSegments('Herr Krause war super', ['Krause'])).toEqual([
            { text: 'Herr ', hit: false }, { text: 'Krause', hit: true }, { text: ' war super', hit: false },
        ])
        expect(highlightSegments('ohne Fund', ['fehlt'])).toEqual([{ text: 'ohne Fund', hit: false }])
    })
})

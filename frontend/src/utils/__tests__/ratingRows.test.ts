import { describe, it, expect } from 'vitest'
import { buildRatingRows } from '../ratingForm'
import type { RatingAnswers, RatingResult } from '../../api/ratingService'

const categories: RatingResult['categories'] = {
  dailyFit: { average: 4.6, count: 7 },
  longDistance: { average: 4.4, count: 6 },
  software: { average: 4.3, count: 7 },
  buildQuality: { average: 3.2, count: 7 },
  service: { average: null, count: 0 },
}

const mine: RatingAnswers = {
  rebuy: 'YES', rebuyReasons: [], dailyFit: 5, longDistance: null, software: 4, buildQuality: 3, service: null,
  totalCost: 'EXPECTED', strengths: [], weaknesses: [],
}

describe('buildRatingRows', () => {
  it('liefert die Sternefragen in Anzeigereihenfolge ohne Kategorien ohne Schnitt', () => {
    const rows = buildRatingRows(categories, null)
    expect(rows.map(r => r.key)).toEqual(['daily_fit', 'long_distance', 'software', 'build_quality'])
    expect(rows[0]).toMatchObject({ average: 4.6, count: 7, mine: null })
  })

  it('markiert die eigene Wertung je Zeile, übersprungene Fragen bleiben ohne Markierung', () => {
    const rows = buildRatingRows(categories, mine)
    expect(rows.find(r => r.key === 'daily_fit')!.mine).toBe(5)
    expect(rows.find(r => r.key === 'long_distance')!.mine).toBeNull()
    expect(rows.find(r => r.key === 'build_quality')!.mine).toBe(3)
  })

  it('ist ohne Kategorien leer', () => {
    expect(buildRatingRows(null, mine)).toEqual([])
  })
})

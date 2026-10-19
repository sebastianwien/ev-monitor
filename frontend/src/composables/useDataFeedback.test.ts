import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../api/axios', () => ({ default: api }))

import { useDataFeedback, resetDataFeedback } from './useDataFeedback'

describe('useDataFeedback', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    resetDataFeedback()
    api.get.mockReset().mockResolvedValue({ data: [] })
    api.put.mockReset().mockResolvedValue({ data: {} })
    api.delete.mockReset().mockResolvedValue({})
  })
  afterEach(() => vi.useRealTimers())

  it('lädt die Bewertungen einmal und ordnet sie Gegenstand und Typ zu', async () => {
    api.get.mockResolvedValue({ data: [{ subjectType: 'LOG', subjectId: 'l1', rating: 'NEGATIVE', tags: ['KWH_WRONG'], comment: null }] })
    const fb = useDataFeedback()
    await fb.load()
    await useDataFeedback().load()

    expect(api.get).toHaveBeenCalledTimes(1)
    expect(fb.ratingOf('LOG', 'l1')).toBe('NEGATIVE')
    expect(fb.ratingOf('TRIP', 'l1')).toBeNull()
  })

  it('Daumen hoch speichert verzögert, zweiter Klick nimmt die Bewertung zurück', async () => {
    const fb = useDataFeedback()
    fb.toggle('LOG', 'l1', 'POSITIVE')
    expect(fb.ratingOf('LOG', 'l1')).toBe('POSITIVE')
    fb.toggle('LOG', 'l1', 'POSITIVE')
    expect(fb.ratingOf('LOG', 'l1')).toBeNull()

    await vi.advanceTimersByTimeAsync(600)
    expect(api.put).not.toHaveBeenCalled()
    expect(api.delete).toHaveBeenCalledWith('/feedback/LOG/l1')
  })

  it('Daumen runter öffnet das Panel, Senden schickt Tags und Kommentar', async () => {
    const fb = useDataFeedback()
    fb.toggle('TRIP', 't1', 'NEGATIVE')
    expect(fb.isPanelOpen('TRIP', 't1')).toBe(true)

    fb.toggleTag('DISTANCE_WRONG')
    fb.draftComment.value = '  war 12 km  '
    await fb.sendNegative('TRIP', 't1')

    expect(api.put).toHaveBeenLastCalledWith('/feedback/TRIP/t1', { rating: 'NEGATIVE', tags: ['DISTANCE_WRONG'], comment: 'war 12 km' })
    expect(fb.isPanelOpen('TRIP', 't1')).toBe(false)
  })

  it('Senden ersetzt einen noch ausstehenden verzögerten Aufruf', async () => {
    const fb = useDataFeedback()
    fb.toggle('LOG', 'l1', 'NEGATIVE')
    await fb.sendNegative('LOG', 'l1')
    await vi.advanceTimersByTimeAsync(600)

    expect(api.put).toHaveBeenCalledTimes(1)
  })
})

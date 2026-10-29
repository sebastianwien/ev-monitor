import { describe, it, expect } from 'vitest'
import { feedTarget } from './storyFeedTarget'
import type { StoryFeedItem } from './storyModel'

const item = (id: StoryFeedItem['id'], { logId, ...params }: Record<string, any>): StoryFeedItem =>
  ({ id, question: 1, relevance: 50, sentiment: 'warn', params, logId }) as StoryFeedItem

describe('feedTarget: wohin eine Zeile unter "Diesen Monat" führt', () => {
  it('genau eine Ladung ohne Preis: diese Ladung direkt bei den Kosten', () => {
    expect(feedTarget(item('data_quality', { priceless: 1, pricelessOpen: 1, logId: 'l1', estimated: 0, total: 4 }), ['cost']))
      .toEqual({ kind: 'charge', logId: 'l1', section: 'cost' })
  })

  it('mehrere Ladungen ohne Preis: die Liste zum Nachtragen', () => {
    expect(feedTarget(item('data_quality', { priceless: 3, pricelessOpen: 3, logId: null, estimated: 0, total: 9 }), ['cost']))
      .toEqual({ kind: 'priceless' })
  })

  it('eine nachtragbar, weitere als unbekannt markiert: die Liste, damit Satz und Ziel zusammenpassen', () => {
    expect(feedTarget(item('data_quality', { priceless: 3, pricelessOpen: 1, logId: 'l1', estimated: 0, total: 9 }), ['cost']))
      .toEqual({ kind: 'priceless' })
  })

  it('nur geschätzte Werte oder Preis als unbekannt markiert: das Kosten-Sheet wie bisher', () => {
    expect(feedTarget(item('data_quality', { priceless: 0, pricelessOpen: 0, estimated: 3, total: 9 }), ['cost'])).toEqual({ kind: 'sheet', id: 'cost' })
    expect(feedTarget(item('data_quality', { priceless: 1, pricelessOpen: 0, logId: null, estimated: 0, total: 4 }), ['cost'])).toEqual({ kind: 'sheet', id: 'cost' })
  })

  it('teuerste Ladung: genau diese Ladung, ohne id das Kosten-Sheet', () => {
    expect(feedTarget(item('most_expensive', { eur: 14.58, avgEur: 5.04, logId: 'l9' }), ['cost'])).toEqual({ kind: 'charge', logId: 'l9' })
    expect(feedTarget(item('most_expensive', { eur: 14.58, avgEur: 5.04 }), ['cost'])).toEqual({ kind: 'sheet', id: 'cost' })
  })

  it('übrige Zeilen öffnen ihr Sheet nur, wenn es das Sheet gibt', () => {
    expect(feedTarget(item('temperature_effect', { deltaPercent: 20 }), ['wltp'])).toEqual({ kind: 'sheet', id: 'wltp' })
    expect(feedTarget(item('temperature_effect', { deltaPercent: 20 }), ['cost'])).toBeNull()
  })
})

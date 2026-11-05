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

  it('teurer Betreiber: das Preis-Sheet dieses Betreibers mit deinem Preis, auch ohne Kachel-Sheet', () => {
    const benchmark = { cpoName: 'Stadtwerke A', chargingType: 'DC', cards: [] }
    const it2 = { ...item('cpo_price', { extraEur: 9 }), operator: { cpoName: 'Stadtwerke A', chargingType: 'DC', pricePerKwh: 0.5, extraEur: 9, card: { name: 'Karte X', variant: null, pricePerKwh: 0.33, monthlyFeeEur: 0, verifiedOn: null, direct: false }, benchmark } } as StoryFeedItem
    expect(feedTarget(it2, [])).toEqual({ kind: 'prices', benchmark, pricePerKwh: 0.5 })
  })

  it('mehr Standverlust: das Strom-Sheet, wo der Stand aufgeschlüsselt ist', () => {
    expect(feedTarget(item('standby_change', { perDay: 0.9, usualPerDay: 0.4 }), ['flow'])).toEqual({ kind: 'sheet', id: 'flow' })
  })

  it('übrige Zeilen öffnen ihr Sheet nur, wenn es das Sheet gibt', () => {
    expect(feedTarget(item('temperature_effect', { deltaPercent: 20 }), ['wltp'])).toEqual({ kind: 'sheet', id: 'wltp' })
    expect(feedTarget(item('temperature_effect', { deltaPercent: 20 }), ['cost'])).toBeNull()
  })
})

import type { StoryFeedItem, TileId } from './storyModel'

/**
 * Wohin eine Zeile unter "Diesen Monat" führt. Wo es um eine bestimmte Ladung geht, öffnet sie diese
 * (fehlender Preis direkt bei den Kosten); bei mehreren Ladungen ohne Preis die Liste zum Nachtragen;
 * sonst das Sheet der Kachel dahinter.
 */
export type FeedTarget =
  | { kind: 'sheet'; id: TileId }
  | { kind: 'charge'; logId: string; section?: 'cost' }
  | { kind: 'priceless' }

const SHEET_OF: Partial<Record<StoryFeedItem['id'], TileId>> = {
  temperature_effect: 'wltp', autumn_forecast: 'range',
  cost_trend: 'cost', most_expensive: 'cost', home_savings: 'cost', data_quality: 'cost', distance_trend: 'cost',
  peer_cost_eur: 'peer',
}

export function feedTarget(item: StoryFeedItem, sheets: TileId[]): FeedTarget | null {
  const p = item.params
  if (item.id === 'most_expensive' && item.logId) return { kind: 'charge', logId: item.logId }
  if (item.id === 'data_quality' && p.pricelessOpen > 0) {
    // Direkt nur, wenn der Satz genau eine Ladung nennt
    return p.priceless === 1 && p.pricelessOpen === 1 && item.logId ? { kind: 'charge', logId: item.logId, section: 'cost' } : { kind: 'priceless' }
  }
  const id = SHEET_OF[item.id]
  return id && sheets.includes(id) ? { kind: 'sheet', id } : null
}

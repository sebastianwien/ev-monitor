import type { StoryFeedItem, TileId } from './storyModel'
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'

/**
 * Wohin eine Zeile unter "Diesen Monat" führt. Fehlt genau einer Ladung der Preis, öffnet sie diese direkt
 * bei den Kosten; bei mehreren Ladungen ohne Preis die Liste zum Nachtragen; ein teurer Betreiber öffnet seine
 * Preise mit den Karten; sonst das Sheet der Kachel dahinter.
 */
export type FeedTarget =
  | { kind: 'sheet'; id: TileId }
  | { kind: 'charge'; logId: string; section?: 'cost' }
  | { kind: 'priceless' }
  | { kind: 'prices'; benchmark: ChargingBenchmark; pricePerKwh: number }

const SHEET_OF: Partial<Record<StoryFeedItem['id'], TileId>> = {
  temperature_effect: 'wltp', autumn_forecast: 'range',
  cost_trend: 'cost', home_savings: 'cost', data_quality: 'cost',
  peer_cost_eur: 'peer',
}

export function feedTarget(item: StoryFeedItem, sheets: TileId[]): FeedTarget | null {
  const p = item.params
  if (item.id === 'cpo_price' && item.operator) return { kind: 'prices', benchmark: item.operator.benchmark, pricePerKwh: item.operator.pricePerKwh }
  if (item.id === 'data_quality' && p.pricelessOpen > 0) {
    // Direkt nur, wenn der Satz genau eine Ladung nennt
    return p.priceless === 1 && p.pricelessOpen === 1 && item.logId ? { kind: 'charge', logId: item.logId, section: 'cost' } : { kind: 'priceless' }
  }
  const id = SHEET_OF[item.id]
  return id && sheets.includes(id) ? { kind: 'sheet', id } : null
}

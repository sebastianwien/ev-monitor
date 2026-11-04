// Thema einer Story-Kachel oder Feed-Zeile für den Probe-Skin v1 (docs ~/private/app-ci/ci-v1-farbregeln.md):
// Geld färbt er amber, Verbrauch violett. Ohne Skin wirkt das Attribut nicht.
import type { FeedId, TileId } from './storyModel'

export type CiTopic = 'money' | 'energy'

const TOPICS: Partial<Record<TileId | FeedId, CiTopic>> = {
  cost: 'money', cost_trend: 'money', home_savings: 'money', peer_cost_eur: 'money', cpo_price: 'money',
  wltp: 'energy', peer: 'energy', flow: 'energy', standby_change: 'energy', temperature_effect: 'energy',
}

export function ciTopic(id: TileId | FeedId): CiTopic | undefined {
  return TOPICS[id]
}

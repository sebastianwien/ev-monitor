import type { TopModelPreview } from '../api/publicModelService'

/**
 * Minimum community logs a model needs to qualify for the efficiency superlative.
 * Below this the "most efficient" winner stands on too thin a data base (e.g. a
 * tiny city car with ~40 logs). At >= 50 a credible champion surfaces and stays
 * stable up to 100, so 50 is the sweet spot.
 */
export const MIN_LOGS_EFFICIENCY = 50

export interface SuperlativeBoard {
  efficiency: TopModelPreview[]
  range: TopModelPreview[]
  popular: TopModelPreview[]
}

/**
 * Picks the three superlative top-3 lists from the raw ranking responses.
 * - efficiency: backend sorts by lowest real consumption; we additionally drop
 *   models below MIN_LOGS_EFFICIENCY, then take the top 3.
 * - range / popular: backend already sorts them; we only take the top 3.
 * Pure selection logic - no formatting, no i18n.
 */
export function selectSuperlatives(
  efficient: TopModelPreview[],
  range: TopModelPreview[],
  popular: TopModelPreview[],
): SuperlativeBoard {
  return {
    efficiency: efficient.filter(m => m.logCount >= MIN_LOGS_EFFICIENCY).slice(0, 3),
    range: range.slice(0, 3),
    popular: popular.slice(0, 3),
  }
}

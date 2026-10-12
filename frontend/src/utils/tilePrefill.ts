import type { LocationQuery } from 'vue-router'

/** Answers a reader tapped on the partner tile (ev-monitor-tile.js), carried to the model page in the link. */
export interface TilePrefill {
  /** ?pendeln= commute per day, there and back */
  commuteKmPerDay?: number
  /** ?urlaub= holiday trip, one way */
  longTripKm?: number
}

const MAX_KM = 5_000

function km(value: LocationQuery[string] | undefined): number | undefined {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return undefined
  const n = Number(value)
  return n > 0 && n <= MAX_KM ? n : undefined
}

export function readTilePrefill(query: LocationQuery): TilePrefill {
  const prefill: TilePrefill = {}
  const commute = km(query.pendeln)
  const trip = km(query.urlaub)
  if (commute != null) prefill.commuteKmPerDay = commute
  if (trip != null) prefill.longTripKm = trip
  return prefill
}

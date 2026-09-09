import api from '../api/axios'
import { tariffLocationParams, type TariffLocationSource } from './tariffLocation'

export interface TariffOptIn extends TariffLocationSource {
  chargingProviderId: string | null
  applyTariffToLocation?: boolean
}

export interface TariffApplied { priced: number; coinsAwarded: number }
const NOTHING: TariffApplied = { priced: 0, coinsAwarded: 0 }

/**
 * Prices every cost-less charge at this location with the selected card - but only if the user
 * ticked the box. Runs after the log itself is saved and swallows its own errors on purpose:
 * a failed backfill must never cast doubt on the charge the user just stored.
 *
 * Returns how many logs were priced and the Watt it earned (both 0 when skipped or failed).
 */
export async function applyTariffToLocationIfRequested(f: TariffOptIn): Promise<TariffApplied> {
  if (!f.applyTariffToLocation || !f.chargingProviderId) return NOTHING

  const location = tariffLocationParams(f)
  if (!location) return NOTHING

  try {
    const res = await api.patch('/logs/apply-tariff-at-location', {
      ...location,
      chargingProviderId: f.chargingProviderId,
    })
    return { priced: res.data?.priced ?? 0, coinsAwarded: res.data?.coinsAwarded ?? 0 }
  } catch {
    return NOTHING
  }
}

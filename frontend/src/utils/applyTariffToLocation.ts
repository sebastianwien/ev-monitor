import api from '../api/axios'
import { tariffLocationParams, type TariffLocationSource } from './tariffLocation'

export interface TariffOptIn extends TariffLocationSource {
  chargingProviderId: string | null
  applyTariffToLocation?: boolean
}

/**
 * Prices every cost-less charge at this location with the selected card - but only if the user
 * ticked the box. Runs after the log itself is saved and swallows its own errors on purpose:
 * a failed backfill must never cast doubt on the charge the user just stored.
 *
 * Returns how many logs were priced (0 when skipped or failed).
 */
export async function applyTariffToLocationIfRequested(f: TariffOptIn): Promise<number> {
  if (!f.applyTariffToLocation || !f.chargingProviderId) return 0

  const location = tariffLocationParams(f)
  if (!location) return 0

  try {
    const res = await api.patch('/logs/apply-tariff-at-location', {
      ...location,
      chargingProviderId: f.chargingProviderId,
    })
    return res.data?.priced ?? 0
  } catch {
    return 0
  }
}

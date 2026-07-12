/**
 * Location parameters for the "apply this card's tariff to every priceless charge here" endpoints.
 *
 * A newly created log carries lat/lon. A stored log carries only its geohash - lat/lon are never
 * persisted (DSGVO), so editing an imported charge leaves the geohash as the only location we have.
 * A freshly picked position wins over the log's old geohash, because the user just moved the charge.
 */
export interface TariffLocationSource {
  latitude: number | null
  longitude: number | null
  isPublicCharging: boolean
  geohash?: string | null
}

export type TariffLocationParams =
  | { lat: number; lon: number; isPublic: boolean }
  | { geohash: string }

export function tariffLocationParams(f: TariffLocationSource): TariffLocationParams | null {
  if (f.latitude != null && f.longitude != null) {
    return { lat: f.latitude, lon: f.longitude, isPublic: f.isPublicCharging }
  }
  if (f.geohash) return { geohash: f.geohash }
  return null
}

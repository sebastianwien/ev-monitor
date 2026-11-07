import api from './axios'

/** Günstigster Katalogpreis je kWh an einer Säule, ohne Grundgebühr. */
export interface StationPrice { pricePerKwh: number; cardName: string; variant: string | null }

export interface MapStation {
  id: string
  name: string
  /** Betreiber im Katalog bekannt; nur dann gibt es Preise */
  known: boolean
  /** Position laut Ladesäulenregister, nur zur Anzeige */
  latitude: number
  longitude: number
  maxAcKw: number | null
  maxDcKw: number | null
  chargePoints: number
  plugTypes: string[]
  address: string | null
  openingHours: string | null
  payment: string | null
  priceAc: StationPrice | null
  priceDc: StationPrice | null
}

export interface StationCell { cell: string; inGermany: boolean; stations: MapStation[] }
/** missing: Felder ohne Antwort (Register oder Drossel), später erneut fragen */
export interface StationMapResponse { cells: StationCell[]; missing: string[] }

export interface StationTariff {
  name: string
  variant: string | null
  pricePerKwh: number
  monthlyFeeEur: number | null
  verifiedOn: string | null
  /** Karte des Betreibers selbst */
  direct: boolean
  sessionFeeEur: number | null
  blockingFeePerMin: number | null
  blockingFeeAfterMin: number | null
  blockingFeeCapEur: number | null
}

export const chargingStationService = {
  /** Säulen je Kartenfeld (Geohash 6); die Karte schickt nie eine Position. */
  async cells(cells: string[], signal?: AbortSignal): Promise<StationMapResponse> {
    const res = await api.get<StationMapResponse>('/charging-stations', { params: { cells: cells.join(',') }, signal })
    return res.data
  },
  /** Alle Katalogtarife an der Säule, günstigste zuerst; null ohne Tarif (204). */
  async tariffs(cpo: string, type: 'AC' | 'DC', powerKw: number | null, signal?: AbortSignal): Promise<StationTariff[] | null> {
    const params: Record<string, string | number> = { cpo, type }
    if (powerKw != null && powerKw >= 1) params.powerKw = Math.round(powerKw)
    const res = await api.get<{ tariffs: StationTariff[] }>('/charging-stations/tariffs', { params, signal })
    return res.status === 204 || !res.data ? null : res.data.tariffs ?? []
  },
}

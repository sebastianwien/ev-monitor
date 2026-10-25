import api from './axios'

/** Eine Karte mit ihrem Preis bei diesem Betreiber: von Fahrern dort bezahlt oder gepflegter Tarif. */
export interface BenchmarkCard {
  name: string
  variant: string | null
  pricePerKwh: number
  /** Nur bei gepflegten Tarifen bekannt, Fahrer tragen die Grundgebühr selten ein. */
  monthlyFeeEur: number | null
  source: 'DRIVERS' | 'CURATED'
  /** Andere Fahrer, die dort mit der Karte geladen haben (nur DRIVERS). */
  drivers: number | null
  /** Stand des gepflegten Tarifs, ISO-Datum (nur CURATED). */
  verifiedOn: string | null
}

/** Was andere Fahrer bei einem Betreiber je kWh zahlen, nur Aggregate. */
export interface ChargingBenchmark {
  cpoName: string
  chargingType: 'AC' | 'DC'
  drivers: number
  medianPricePerKwh: number
  lowerQuartilePricePerKwh: number
  upperQuartilePricePerKwh: number
  /** Günstigste zuerst, höchstens fünf. */
  cards: BenchmarkCard[]
}

export const chargingBenchmarkService = {
  /** null bei 204: zu wenige andere Fahrer dort oder kein bekannter Betreiber. */
  async get(cpo: string, type: 'AC' | 'DC'): Promise<ChargingBenchmark | null> {
    const res = await api.get<ChargingBenchmark>('/charging-benchmarks', { params: { cpo, type } })
    return res.status === 204 ? null : res.data
  },
}

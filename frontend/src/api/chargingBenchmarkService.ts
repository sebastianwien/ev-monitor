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

/** Eine gepflegte Ladekarte, die bei dieser Ladung mindestens einen Euro gespart hätte. */
export interface CardSavingOffer {
  name: string
  variant: string | null
  pricePerKwh: number
  monthlyFeeEur: number
  /** Stand des Tarifs, ISO-Datum. */
  verifiedOn: string | null
  /** Ersparnis bei dieser Ladung, Grundgebühr anteilig schon abgezogen. */
  chargeSavingsEur: number
  /** Anteil der Grundgebühr, der auf diese Ladung fällt. */
  feeShareEur: number
  /** Ersparnis über die 30 Tage bis zu dieser Ladung, abzüglich der vollen Grundgebühr. */
  monthSavingsEur: number
}

/** Was gepflegte Ladekarten bei einer eigenen Ladung gespart hätten; größte Ersparnis zuerst. */
export interface CardSavings {
  logId: string
  cpoName: string
  chargingType: 'AC' | 'DC'
  paidPerKwh: number
  kwh: number
  offers: CardSavingOffer[]
}

export const chargingBenchmarkService = {
  /** null bei 204: nichts belegbar (Ausland, ohne Ort, ohne Säulen-kWh, keine Karte spart einen Euro). */
  async savings(logId: string): Promise<CardSavings | null> {
    const res = await api.get<CardSavings>('/charging-benchmarks/savings', { params: { logId } })
    return res.status === 204 ? null : res.data
  },
  /** null bei 204: zu wenige andere Fahrer dort oder kein bekannter Betreiber. */
  async get(cpo: string, type: 'AC' | 'DC'): Promise<ChargingBenchmark | null> {
    const res = await api.get<ChargingBenchmark>('/charging-benchmarks', { params: { cpo, type } })
    return res.status === 204 ? null : res.data
  },
}

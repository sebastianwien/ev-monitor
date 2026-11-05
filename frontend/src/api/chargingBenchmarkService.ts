import api from './axios'

/** Ein gepflegter, heute gültiger Tarif an diesem Betreiber. */
export interface BenchmarkCard {
  name: string
  variant: string | null
  pricePerKwh: number
  monthlyFeeEur: number
  /** Stand des Tarifs, ISO-Datum. */
  verifiedOn: string | null
  /** Der Betreiber selbst (App oder Ad-hoc), keine fremde Ladekarte. */
  direct: boolean
}

/** Gepflegte Tarife an einem Betreiber je Ladeart: "Was hätte ich hier mit einer anderen Karte gezahlt?" */
export interface ChargingBenchmark {
  cpoName: string
  chargingType: 'AC' | 'DC'
  /** Günstigste zuerst. */
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
  /** null bei 204: unbekannter Betreiber oder kein gepflegter Tarif dort. */
  async get(cpo: string, type: 'AC' | 'DC'): Promise<ChargingBenchmark | null> {
    const res = await api.get<{ cpoName: string; chargingType: 'AC' | 'DC'; tariffs: BenchmarkCard[] }>(
      '/charging-benchmarks/tariffs', { params: { cpo, type } })
    if (res.status === 204 || !res.data) return null
    return { cpoName: res.data.cpoName, chargingType: res.data.chargingType, cards: res.data.tariffs ?? [] }
  },
}

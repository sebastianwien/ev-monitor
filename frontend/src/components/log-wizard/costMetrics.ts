import type { ConsumptionPreview } from '../../utils/consumptionPreview'

/** Eine Kennzahl der Kostenzeile: Wert und Einheit getrennt, damit das Template den Wert fett setzen kann. */
export interface CostMetric { value: string; unit: string; tone?: 'notice' }

export interface CostMetricFormat {
  isEurCountry: boolean
  /** Untereinheit (ct) in Euro-Ländern, sonst das Währungssymbol */
  subunit: string
  symbol: string
  eurToLocal: (eur: number) => number
  formatNumber: (n: number) => string
  formatDecimal: (n: number, digits: number) => string
}

/**
 * Preis je kWh (Euro-Länder in Cent), Kosten je 100 km und Verbrauch je 100 km - in dieser Reihenfolge,
 * jeweils nur, wenn berechenbar. Geteilt von Kostenbox (Schritt 2) und Kosten-Kachel (Zusammenfassung).
 */
export function costMetrics(perKwhLocal: number | null, preview: ConsumptionPreview | null | undefined, f: CostMetricFormat): CostMetric[] {
  const m: CostMetric[] = []
  if (perKwhLocal != null) {
    m.push(f.isEurCountry
      ? { value: f.formatNumber(Math.round(perKwhLocal * 100)), unit: `${f.subunit}/kWh` }
      : { value: f.formatDecimal(perKwhLocal, 2), unit: `${f.symbol}/kWh` })
  }
  if (preview?.eurPer100km != null) m.push({ value: f.formatDecimal(f.eurToLocal(preview.eurPer100km), 2), unit: `${f.symbol}/100 km` })
  if (preview) m.push({ value: f.formatNumber(Math.round(preview.kwhPer100km * 10) / 10), unit: 'kWh/100 km', tone: preview.plausible ? undefined : 'notice' })
  return m
}

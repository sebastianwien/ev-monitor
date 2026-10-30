import { formatDuration } from './powerCurveScrub'

/**
 * Kopfsatz des Ladekurven-Fensters ("In 16 Min von 8 auf 66 %").
 *
 * Liefert den i18n-Fall samt Werten statt fertigem Text, damit die Übersetzung
 * beim Aufrufer bleibt. Der Ladestand hat Vorrang, weil er ohne kWh-Wissen
 * verständlich ist; die Energie springt nur ein, wenn er fehlt.
 */
export type ChargeHeadline =
  | { key: 'soc'; duration: string; from: number; to: number }
  | { key: 'energy'; duration: string; kwh: number }
  | { key: 'duration'; duration: string }

export interface HeadlineInput {
  durationMs: number | null
  socBefore: number | null | undefined
  socAfter: number | null | undefined
  kwh: number | null | undefined
}

export function chargeHeadline({ durationMs, socBefore, socAfter, kwh }: HeadlineInput): ChargeHeadline | null {
  if (!durationMs || durationMs <= 0) return null
  const duration = formatDuration(durationMs)
  if (socBefore != null && socAfter != null) {
    const from = Math.round(socBefore)
    const to = Math.round(socAfter)
    if (to > from) return { key: 'soc', duration, from, to }
  }
  if (kwh != null && kwh > 0) return { key: 'energy', duration, kwh }
  return { key: 'duration', duration }
}

/** Nachgeladene Reichweite in ganzen km - aus der Log-Energie, wie "Geladen" daneben. */
export function addedRangeKm(kwh: number | null | undefined, consumptionKwhPer100km: number | null | undefined): number | null {
  if (kwh == null || kwh <= 0 || consumptionKwhPer100km == null || consumptionKwhPer100km <= 0) return null
  return Math.round((kwh * 100) / consumptionKwhPer100km)
}

type Translate = (key: string, params: Record<string, unknown>) => string

/** Übersetzter Kopfsatz - gemeinsam für das Ladekurven-Sheet und die öffentliche Teilen-Seite. */
export function chargeHeadlineText(h: ChargeHeadline | null, t: Translate, formatKwh: (kwh: number) => string): string | null {
  if (!h) return null
  switch (h.key) {
    case 'soc': return t('dashboard.power_curve_headline_soc', { duration: h.duration, from: h.from, to: h.to })
    case 'energy': return t('dashboard.power_curve_headline_energy', { duration: h.duration, kwh: formatKwh(h.kwh) })
    default: return t('dashboard.power_curve_headline_duration', { duration: h.duration })
  }
}

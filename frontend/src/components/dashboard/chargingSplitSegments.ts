import type { LocationSplit } from '../../composables/useDashboardStats'

export interface Segment {
  label: string
  kwh: number
  pct: number
  textColorClass: string
  bgClass: string
}

export interface LocationLabels {
  public: string
  private: string
  unknown: string
}

/**
 * Ladeort-Segmente fuer die Split-Kachel.
 *
 * Seit V166 ist der Ladeort dreiwertig: oeffentlich, daheim, unbekannt. Automatisch
 * erzeugte Logs tragen ihn haeufig nicht - die Tesla-Telemetrie meldet nur DC sicher
 * und laesst AC bewusst offen. Der Unbekannt-Topf zaehlt deshalb in die Gesamtmenge
 * mit ein, damit die Heimquote nicht zu hoch erscheint, wird aber nur angezeigt,
 * wenn er tatsaechlich Energie enthaelt.
 */
export function locationSegments(split: LocationSplit, labels: LocationLabels): Segment[] {
  const unknownKwh = split.unknownKwh ?? 0
  const total = split.publicKwh + split.privateKwh + unknownKwh
  if (total <= 0) return []

  const pct = (kwh: number) => Math.round((kwh / total) * 100)

  const segments: Segment[] = [
    {
      label: labels.public,
      kwh: split.publicKwh,
      pct: pct(split.publicKwh),
      textColorClass: 'text-slate-500 dark:text-slate-400',
      bgClass: 'bg-slate-400 dark:bg-slate-500',
    },
    {
      label: labels.private,
      kwh: split.privateKwh,
      pct: pct(split.privateKwh),
      textColorClass: 'text-slate-700 dark:text-slate-200',
      bgClass: 'bg-slate-600 dark:bg-slate-300',
    },
  ]

  if (unknownKwh > 0) {
    segments.push({
      label: labels.unknown,
      kwh: unknownKwh,
      pct: pct(unknownKwh),
      textColorClass: 'text-slate-400 dark:text-slate-500',
      bgClass: 'bg-slate-300 dark:bg-slate-600',
    })
  }

  return segments
}

/**
 * Diff-Markierung fuer den Admin-Webhook-Inspector: welche Zellen haben sich
 * gegenueber dem chronologisch vorherigen Webhook geaendert?
 * Die Liste ist newest-first sortiert, der Vorgaenger von rows[i] ist rows[i+1].
 */
export interface WebhookSignal {
  value: string | null
  oemUpdatedAt: number | null
  status: string | null
}

/**
 * Was der Detektor (ChargingRunDetector, connectors) an dieser Zeile tut. Serverseitig
 * berechnet - die Regeln leben ausschliesslich im Backend, hier wird nur gezeichnet.
 */
/** Kennzahlen eines abgeschlossenen Laufs, unveraendert aus CompletedRun. */
export interface RunSummary {
  endReason: string
  startedAt: string | null
  endedAt: string | null
  energyKwh: number | null
  energySource: string | null
  counterKwh: number | null
  socStart: number | null
  socEnd: number | null
  socStartBackfilled: boolean
}

export interface WebhookDetection {
  inRun: boolean
  runStart: boolean
  runEnd: boolean
  endedBeforeEvent: boolean
  completedRuns: RunSummary[]
}

export interface WebhookRow {
  id: string
  receivedAt: string
  odometer: WebhookSignal
  energyAdded: WebhookSignal
  soc: WebhookSignal
  isCharging: WebhookSignal
  detection: WebhookDetection | null
}

/** Dauer eines Laufs in Minuten; null, wenn ein Zeitstempel fehlt. */
export function runDurationMinutes(run: RunSummary): number | null {
  if (!run.startedAt || !run.endedAt) return null
  const millis = new Date(run.endedAt).getTime() - new Date(run.startedAt).getTime()
  return millis < 0 ? null : Math.round(millis / 60000)
}

/** SoC-Hub in Prozentpunkten; null, wenn Start oder Ende fehlt. */
export function socDelta(run: RunSummary): number | null {
  if (run.socStart === null || run.socEnd === null) return null
  return run.socEnd - run.socStart
}

/** Welches Stueck der Lauf-Linie in dieser Zelle gezeichnet wird. */
export type RunSegment = 'none' | 'full' | 'top' | 'bottom'

/**
 * Die Tabelle ist newest-first: ein Ladevorgang startet in der untersten Zeile und waechst
 * nach oben. Die Startzeile zeichnet daher nach oben, die Endzeile nach unten. Endet ein Lauf
 * schon vor dem Event (Timeout, Odometer, Zaehler-Drop), gehoert die Zeile nicht mehr zu ihm.
 */
export function runSegment(detection?: WebhookDetection | null): RunSegment {
  if (!detection) return 'none'
  if (detection.runStart && detection.runEnd) {
    return detection.endedBeforeEvent ? 'top' : 'full'
  }
  if (detection.runStart) return 'top'
  if (detection.runEnd) return detection.inRun ? 'bottom' : 'none'
  return detection.inRun ? 'full' : 'none'
}

export const SIGNAL_KEYS = ['odometer', 'energyAdded', 'soc', 'isCharging'] as const
export type SignalKey = (typeof SIGNAL_KEYS)[number]

export interface CellMarks {
  value: boolean
  oem: boolean
  status: boolean
}

export type RowMarks = Record<SignalKey, CellMarks>

const NO_CHANGE: CellMarks = { value: false, oem: false, status: false }

function diffSignal(current: WebhookSignal, previous: WebhookSignal): CellMarks {
  return {
    value: current.value !== previous.value,
    oem: current.oemUpdatedAt !== previous.oemUpdatedAt,
    status: current.status !== previous.status,
  }
}

export function changedCells(rows: WebhookRow[]): RowMarks[] {
  return rows.map((row, i) => {
    const previous = rows[i + 1]
    if (!previous) {
      return { odometer: NO_CHANGE, energyAdded: NO_CHANGE, soc: NO_CHANGE, isCharging: NO_CHANGE }
    }
    return {
      odometer: diffSignal(row.odometer, previous.odometer),
      energyAdded: diffSignal(row.energyAdded, previous.energyAdded),
      soc: diffSignal(row.soc, previous.soc),
      isCharging: diffSignal(row.isCharging, previous.isCharging),
    }
  })
}

/**
 * Ladegruppen: Ladungen, die zusammen eine Ladung sind. go-e- und API-Ladungen ohne km-Stand am selben Tag
 * (Überschussladen) und aufeinanderfolgende Ladungen am selben km-Stand (Nachladen). Die Liste baut daraus
 * ihre Gruppen-Einträge, Logbuch und Story ihre Bündel.
 */
export interface GroupLog {
  id: string
  loggedAt: string
  dataSource?: string | null
  odometerKm?: number | null
  includeInStatistics?: boolean
  consumptionImplausible?: boolean
}

export interface ChargeGroups<T> {
  /** go-e und API ohne km-Stand, je Tag, ab zwei */
  byDay: T[][]
  /** gleicher km-Stand hintereinander, neueste zuerst */
  byOdometer: T[][]
  /** alle übrigen; ohne implausible Ladungen, die nicht in die Statistik zählen */
  singles: T[]
}

const localDay = (iso: string) => {
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function chargeGroupsOf<T extends GroupLog>(logs: T[]): ChargeGroups<T> {
  // go-e/API ohne km-Stand: je Tag
  const goeByDay = new Map<string, T[]>()
  for (const log of logs) {
    if ((log.dataSource !== 'WALLBOX_GOE' && log.dataSource !== 'API_UPLOAD') || log.odometerKm != null) continue
    const day = localDay(log.loggedAt)
    if (!goeByDay.has(day)) goeByDay.set(day, [])
    goeByDay.get(day)!.push(log)
  }
  const byDay = [...goeByDay.values()].filter(g => g.length >= 2)
  const grouped = new Set(byDay.flat().map(l => l.id))

  // Übrige: gleicher km-Stand hintereinander
  const sorted = logs
    .filter(l => !grouped.has(l.id))
    .filter(l => l.includeInStatistics || !l.consumptionImplausible)
    .sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())
  const byOdometer: T[][] = []
  const singles: T[] = []
  let i = 0
  while (i < sorted.length) {
    let j = i + 1
    if (sorted[i].odometerKm != null) {
      while (j < sorted.length && sorted[j].odometerKm != null && sorted[j].odometerKm === sorted[i].odometerKm) j++
    }
    if (j > i + 1) byOdometer.push(sorted.slice(i, j))
    else singles.push(sorted[i])
    i = j
  }
  return { byDay, byOdometer, singles }
}

/** Je Ladung einer Ladegruppe die Kennung der Gruppe; einzelne Ladungen fehlen. */
export function groupKeysOf(logs: GroupLog[]): Map<string, string> {
  const { byDay, byOdometer } = chargeGroupsOf(logs)
  const keys = new Map<string, string>()
  for (const group of [...byDay, ...byOdometer]) for (const l of group) keys.set(l.id, group[0].id)
  return keys
}

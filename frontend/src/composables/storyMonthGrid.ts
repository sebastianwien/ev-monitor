import type { MomentPoint } from './storyMoments'

/**
 * Monatsraster hinter dem Kalender-Segment des Monats-Schalters: weiter zurück als die drei Monate des Schalters,
 * ohne zwölf Chips. Grundlage ist die Tagesreihe über die Lebenszeit (Auto-Paket), kein eigener Abruf.
 */
export interface GridMonth { year: number; month: number; offset: number; enabled: boolean }
export interface GridYear { year: number; months: GridMonth[] }

/** Die drei Monate des Schalters gibt es immer, auch ohne Ladung. */
const SWITCH_OFFSET = -2

/** Abstand zum laufenden Monat: 0 laufend, -1 Vormonat. */
export function monthOffset(year: number, month: number, now: Date): number {
  return (year - now.getFullYear()) * 12 + (month - 1 - now.getMonth())
}

/** Monate mit mindestens einer Ladung, als 'Jahr-Monat'. */
export function chargeMonths(points: MomentPoint[]): Set<string> {
  const months = new Set<string>()
  for (const p of points) {
    if ((p.charges ?? 1) <= 0) continue
    const d = new Date(p.timestamp)
    if (!Number.isNaN(d.getTime())) months.add(`${d.getFullYear()}-${d.getMonth() + 1}`)
  }
  return months
}

/** Frühester wählbarer Monat als Abstand: die erste Ladung, mindestens die drei Monate des Schalters. */
export function earliestOffset(months: Set<string>, now: Date): number {
  let min = SWITCH_OFFSET
  for (const key of months) {
    const [y, m] = key.split('-').map(Number)
    min = Math.min(min, monthOffset(y, m, now))
  }
  return min
}

/** Jahre absteigend, je zwölf Monate; wählbar sind Monate mit Ladung bis heute und die drei des Schalters. */
export function monthGrid(now: Date, months: Set<string>, minOffset: number): GridYear[] {
  const first = new Date(now.getFullYear(), now.getMonth() + minOffset, 1).getFullYear()
  const years: GridYear[] = []
  for (let year = now.getFullYear(); year >= first; year--) {
    years.push({
      year,
      months: Array.from({ length: 12 }, (_, i) => {
        const offset = monthOffset(year, i + 1, now)
        const enabled = offset <= 0 && offset >= minOffset && (offset >= SWITCH_OFFSET || months.has(`${year}-${i + 1}`))
        return { year, month: i + 1, offset, enabled }
      }),
    })
  }
  return years
}

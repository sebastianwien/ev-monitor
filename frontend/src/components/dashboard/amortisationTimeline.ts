/**
 * Zeitachse der Amortisationsschiene.
 *
 * Spannt vom ersten erfassten Ladevorgang bis zu dem Zeitpunkt, an dem die Ersparnis die
 * Investition eingeholt hat. Das Ende ist eine Fortschreibung des heutigen Ladeverhaltens,
 * keine Zusage - es wird deshalb bewusst grob angegeben (Anfang/Mitte/Ende eines Jahres)
 * statt auf den Tag. Eine tagesgenaue Angabe wuerde eine Sicherheit behaupten, die die
 * Rechnung nicht hat.
 */

export interface AmortisationTimeline {
  startYear: number
  /** null, sobald die Investition eingespielt ist. */
  endYear: number | null
  endPart: 'early' | 'mid' | 'late' | null
  totalYears: number
  progressPct: number
  /** Position der Jahresstriche in Prozent, ohne Anfang und Ende. */
  tickPercents: number[]
}

/** Mehr Striche werden auf schmalen Kacheln zu Rauschen. */
const MAX_TICKS = 12

export function amortisationTimeline(input: {
  /** Erstes Jahr mit belegtem Heimladen - kommt aus den Logs, nicht aus einer Annahme. */
  startYear: number
  yearsRemaining: number | null
  now: Date
}): AmortisationTimeline | null {
  const { startYear, yearsRemaining, now } = input
  if (yearsRemaining == null) return null

  const used = Math.max(0, now.getFullYear() - startYear
    + (now.getMonth() + now.getDate() / 31) / 12)
  const totalYears = used + Math.max(0, yearsRemaining)
  if (totalYears <= 0) return null

  const progressPct = Math.max(0, Math.min(100, (used / totalYears) * 100))

  const done = yearsRemaining <= 0
  const end = done ? null : addYears(now, yearsRemaining)

  // Bei langen Laufzeiten jeden zweiten, dritten ... Strich zeichnen, damit die Schiene
  // lesbar bleibt.
  const step = Math.ceil((totalYears - 1) / MAX_TICKS) || 1
  const tickPercents: number[] = []
  for (let year = step; year < totalYears; year += step) {
    tickPercents.push((year / totalYears) * 100)
  }

  return {
    startYear,
    endYear: end ? end.getFullYear() : null,
    endPart: end ? partOfYear(end.getMonth()) : null,
    totalYears,
    progressPct,
    tickPercents,
  }
}

function addYears(date: Date, years: number): Date {
  return new Date(date.getTime() + years * 365.25 * 24 * 60 * 60 * 1000)
}

/** Drittel des Jahres - feiner waere bei einer Fortschreibung nicht ehrlich. */
function partOfYear(monthIndex: number): 'early' | 'mid' | 'late' {
  if (monthIndex <= 3) return 'early'
  if (monthIndex <= 7) return 'mid'
  return 'late'
}

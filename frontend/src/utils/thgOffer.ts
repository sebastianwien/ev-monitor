/**
 * THG-Prämie über den Partner Geld-für-eAuto: Saisons, Text und Ausblenden, geteilt von
 * Banner, Dashboard-Kachel, Onboarding und Story. Nur Deutschland (prüft der Aufrufer).
 * Daten in Ortszeit, beide Grenzen inklusive. Neue Saison: Zeile anhängen, sobald der
 * Partner Betrag und Start freigibt (Sperrfristen beachten).
 */
export interface ThgSeason {
  /** Quotenjahr, für das beantragt wird. */
  year: number
  from: string
  until: string
  /** Garantieprämie des Partners; null, solange kein Betrag genannt werden darf. */
  amountEur: number | null
}

export const THG_URL = 'https://Geld-fuer-eAuto.de/ref/evmonitor'

export const THG_SEASONS: ThgSeason[] = [
  { year: 2026, from: '2026-01-01', until: '2026-11-07', amountEur: null },
  // Frist 2027 noch nicht bekannt, Annahme wie 2026; mit dem Partner abgleichen.
  { year: 2027, from: '2026-11-08', until: '2027-11-07', amountEur: 230 },
]

const DISMISS_MS = 90 * 86_400_000

function localDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function startOf(day: string): number {
  return new Date(`${day}T00:00:00`).getTime()
}

export function thgSeason(now: Date): ThgSeason | null {
  const day = localDay(now)
  return THG_SEASONS.find(s => s.from <= day && day <= s.until) ?? null
}

export function thgCopy(season: ThgSeason): { title: string; text: string; cta: string } {
  return {
    title: season.amountEur != null
      ? `${season.amountEur} € für dein E-Auto, einmal im Jahr.`
      : `THG-Prämie ${season.year} schon beantragt?`,
    text: `THG-Prämie ${season.year} jetzt beantragen. Über diesen Link unterstützt du auch ev-monitor.`,
    cta: 'Prämie beantragen',
  }
}

/** Weggeklickt gilt 90 Tage, aber nie über den Start einer neuen Saison hinaus. */
export function thgDismissActive(dismissedAt: number | null, now: Date, season: ThgSeason): boolean {
  if (!dismissedAt) return false
  if (dismissedAt < startOf(season.from)) return false
  return now.getTime() - dismissedAt < DISMISS_MS
}

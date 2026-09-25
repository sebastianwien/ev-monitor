import { relativeTimeParts } from './recentActivity'

/** Antwort von GET /api/admin/stats/imports (Import-Protokoll, Herstellerarchitektur R2b). */
export type ImportOutcome = 'IMPORTED' | 'NO_NEW_DATA' | 'PARTIAL' | 'FAILED' | 'REJECTED' | 'PARSE_ERROR'
export type ImportHealth = 'OK' | 'WARN' | 'ERROR'

export interface ImportTopError {
  error: string
  count: number
  lastAt: string
}

export interface ImportGroup {
  provider: string
  channel: string
  health: ImportHealth
  events: number
  errorRate: number
  sessionsImported: number
  sessionsSkipped: number
  sessionsFailed: number
  tripsImported: number
  tripsSkipped: number
  outcomes: Partial<Record<ImportOutcome, number>>
  lastEventAt: string | null
  lastSuccessAt: string | null
  topErrors: ImportTopError[]
}

export interface ImportDailyCount {
  date: string
  outcome: ImportOutcome
  count: number
}

export interface ImportStats {
  days: number
  groups: ImportGroup[]
  daily: ImportDailyCount[]
}

export const HEALTH_LABEL: Record<ImportHealth, string> = { OK: 'OK', WARN: 'Auffällig', ERROR: 'Gestört' }

/** Reihenfolge im Diagramm: Erfolg unten, Störungen oben. */
const OUTCOME_ORDER: ImportOutcome[] = ['IMPORTED', 'NO_NEW_DATA', 'PARTIAL', 'REJECTED', 'FAILED', 'PARSE_ERROR']

export const OUTCOME_LABEL: Record<ImportOutcome, string> = {
  IMPORTED: 'Importiert',
  NO_NEW_DATA: 'Nichts Neues',
  PARTIAL: 'Teilweise',
  FAILED: 'Fehlgeschlagen',
  REJECTED: 'Abgelehnt',
  PARSE_ERROR: 'Nicht lesbar',
}

export const OUTCOME_COLOR: Record<ImportOutcome, string> = {
  IMPORTED: '#22c55e',
  NO_NEW_DATA: '#6b7280',
  PARTIAL: '#f59e0b',
  FAILED: '#ef4444',
  REJECTED: '#a855f7',
  PARSE_ERROR: '#f97316',
}

const PROVIDER_LABEL: Record<string, string> = {
  MANUAL: 'Manuell',
  SPRITMONITOR: 'Spritmonitor',
  TESLA: 'Tesla',
  OCPP_WALLBOX: 'OCPP-Wallbox',
  GOE: 'go-e',
  PUBLIC_API: 'Public API',
  TRONITY: 'Tronity',
  SMARTCAR: 'Smartcar',
  VW_GROUP: 'VW Group',
  TESSIE: 'Tessie',
  XPENG: 'XPeng',
  UNKNOWN: 'Unbekannt',
}

const CHANNEL_LABEL: Record<string, string> = {
  MANUAL: 'Manuell',
  UPLOAD: 'Upload',
  SYNC: 'Sync',
  LIVE: 'Live',
  UNKNOWN: 'Unbekannt',
}

const HEALTH_RANK: Record<ImportHealth, number> = { ERROR: 0, WARN: 1, OK: 2 }

/** Gestörte Quellen zuerst, innerhalb der Ampelfarbe nach Aufrufen. */
export function sortGroups(groups: ImportGroup[]): ImportGroup[] {
  return [...groups].sort((a, b) => HEALTH_RANK[a.health] - HEALTH_RANK[b.health] || b.events - a.events)
}

export function sourceLabel(provider: string, channel: string): string {
  return `${PROVIDER_LABEL[provider] ?? provider} · ${CHANNEL_LABEL[channel] ?? channel}`
}

const rateFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 })

export function formatRate(rate: number): string {
  return `${rateFormat.format(rate * 100)} %`
}

const relativeFormat = new Intl.RelativeTimeFormat('de', { style: 'short' })

export function formatAgo(iso: string | null, nowMs: number = Date.now()): string {
  if (!iso) return 'nie'
  const parts = relativeTimeParts(Date.parse(iso), nowMs)
  return parts ? relativeFormat.format(parts.value, parts.unit) : 'nie'
}

const pad = (n: number) => String(n).padStart(2, '0')
const isoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

/** Ein Balken je Tag im Zeitraum (auch leere Tage), gestapelt nach Ergebnis. */
export function dailySeries(daily: ImportDailyCount[], days: number, today: Date = new Date()) {
  const dates: string[] = []
  const labels: string[] = []
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i)
    dates.push(isoDate(d))
    labels.push(`${pad(d.getDate())}.${pad(d.getMonth() + 1)}.`)
  }
  const present = new Set(daily.map((d) => d.outcome))
  const datasets = OUTCOME_ORDER.filter((o) => present.has(o)).map((outcome) => ({
    outcome,
    data: dates.map((date) =>
      daily.filter((d) => d.date === date && d.outcome === outcome).reduce((sum, d) => sum + d.count, 0),
    ),
  }))
  return { labels, datasets }
}

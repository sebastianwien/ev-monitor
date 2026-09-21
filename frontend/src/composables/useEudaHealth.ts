import type { EudaSyncActivity } from '../api/euDataActSyncService'

/**
 * Lagebild einer Data-Act-Verbindung, aus dem Sync-Protokoll abgeleitet. Steuert Hinweistext,
 * Farbe und ob die Beschwerde an den Hersteller prominent angeboten wird.
 * Reihenfolge = Prioritaet: ein Zustand, der den Nutzer zum Handeln zwingt, schlaegt einen, der
 * nur Geduld braucht.
 */
export type EudaHealth =
  | 'AUTH_FAILED'
  | 'PAUSED'
  | 'NO_REQUEST'
  | 'FAILING'
  | 'HISTORY_FAILED'
  | 'WAITING_FIRST'
  | 'NO_CONTENT'
  | 'STALE'
  | 'HEALTHY'

const HOUR = 3_600_000
/** Bis dahin ist "noch nichts" normal - das Portal braucht Stunden bis zur ersten Lieferung. */
export const WAITING_WINDOW_MS = 24 * HOUR
/** Danach gilt eine Verbindung, die schon Inhalt hatte, als eingeschlafen. */
export const STALE_WINDOW_MS = 72 * HOUR
export const FAILING_THRESHOLD = 3

/** Zustaende, in denen der Hersteller in der Pflicht ist - dort ist die Beschwerde der naechste Schritt. */
export const MANUFACTURER_AT_FAULT: ReadonlySet<EudaHealth> = new Set(['NO_REQUEST', 'NO_CONTENT', 'STALE'])

export function classifyEudaHealth(activity: EudaSyncActivity, now: Date = new Date()): EudaHealth {
  const c = activity.connection
  if (c.status === 'AUTH_FAILED') return 'AUTH_FAILED'
  if (c.status === 'EXPIRED') return 'PAUSED'
  if (!c.dataRequestActive) return 'NO_REQUEST'
  if (c.consecutiveFailures >= FAILING_THRESHOLD) return 'FAILING'
  if (c.history?.attemptsExhausted) return 'HISTORY_FAILED'

  const hadContent = activity.summary.deliveriesWithContent > 0 || c.lastDataAt !== null
  if (!hadContent) {
    const age = now.getTime() - new Date(c.connectedAt).getTime()
    return age < WAITING_WINDOW_MS ? 'WAITING_FIRST' : 'NO_CONTENT'
  }
  if (c.lastDataAt && now.getTime() - new Date(c.lastDataAt).getTime() > STALE_WINDOW_MS) return 'STALE'
  return 'HEALTHY'
}

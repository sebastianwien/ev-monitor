import type { VwEudaHistoryState } from '../api/vwEudaSyncService'
import { MANUFACTURER_AT_FAULT, type VwEudaHealth } from './useVwEudaHealth'

/**
 * Genau eine primäre Handlung je Zustand der Data-Act-Verbindung. Reihenfolge = Dringlichkeit:
 * ohne Abo läuft nichts, ohne Login läuft nichts, dann ist der Hersteller dran, dann die Historie.
 */
export type VwEudaPrimaryAction = 'upgrade' | 'relogin' | 'complaint' | 'history' | null

export interface PrimaryActionInput {
  status: 'ACTIVE' | 'AUTH_FAILED' | 'EXPIRED'
  /** null, wenn das Protokoll nicht geladen werden konnte. */
  health: VwEudaHealth | null
  /** Ein Beschwerde-Mailtext liegt vor (braucht das Protokoll). */
  hasComplaint: boolean
  historyOpen: boolean
}

export function deriveVwEudaPrimaryAction(i: PrimaryActionInput): VwEudaPrimaryAction {
  if (i.status === 'EXPIRED') return 'upgrade'
  if (i.status === 'AUTH_FAILED') return 'relogin'
  if (i.health && MANUFACTURER_AT_FAULT.has(i.health) && i.hasComplaint) return 'complaint'
  if (i.historyOpen) return 'history'
  return null
}

/** Historie ist offen, solange sie weder importiert ist noch gerade läuft oder eben angefordert wurde. */
export function isVwEudaHistoryOpen(
  history: VwEudaHistoryState | null | undefined,
  importedAtFromStatus: string | null,
  historyPending: boolean,
): boolean {
  if (history?.importedAt ?? importedAtFromStatus) return false
  if (history?.running || historyPending) return false
  return true
}

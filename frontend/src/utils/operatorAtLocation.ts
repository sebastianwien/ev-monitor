import api from '../api/axios'

export interface OperatorGapQuery { geohash: string; cpoName: string; excludeLogId: string }

interface StoredLog { id: string; geohash: string | null; cpoName: string | null }
interface EditedPlace { isPublicCharging: boolean; cpoName: string | null }

/**
 * Wann die Abfrage "Auch für N weitere Ladungen hier" Sinn ergibt: öffentlich, Betreiber neu oder
 * geändert, und die Ladung hat einen gespeicherten Ort. Gezählt wird am bisherigen Geohash - das ist
 * der Ort, an dem die anderen Ladungen liegen, auch wenn diese Ladung auf eine Säule umzieht.
 * Ob der Ort eindeutig ist, entscheidet der Server (0 = nicht eindeutig).
 */
export function operatorGapQuery(log: StoredLog, form: EditedPlace): OperatorGapQuery | null {
  const cpoName = form.cpoName?.trim()
  if (!form.isPublicCharging || !cpoName || !log.geohash) return null
  if (cpoName === log.cpoName?.trim()) return null
  return { geohash: log.geohash, cpoName, excludeLogId: log.id }
}

export async function fetchOperatorGapCount(q: OperatorGapQuery): Promise<number> {
  try {
    const res = await api.get('/logs/operator-gaps', { params: q })
    return res.data?.count ?? 0
  } catch {
    return 0
  }
}

/** Läuft nach dem Speichern der Ladung selbst und schluckt eigene Fehler: der Nachtrag darf die gespeicherte Ladung nie in Frage stellen. */
export async function applyOperatorToLocation(q: OperatorGapQuery): Promise<number> {
  try {
    const res = await api.patch('/logs/apply-operator-at-location', q)
    return res.data?.updated ?? 0
  } catch {
    return 0
  }
}

import { ref } from 'vue'
import api from '../api/axios'
import { useLogsRefreshStore } from '../stores/logsRefresh'

const UNDO_WINDOW_MS = 10_000

interface PendingUndo {
  id: string
  /** Ladung oder Fahrt: bestimmt Endpunkt und Text im Toast */
  kind: 'log' | 'trip'
  onChanged?: () => unknown
}

// Modulweit: ein Toast in App.vue, egal aus welcher Ansicht gelöscht wurde.
const pending = ref<PendingUndo | null>(null)
const restoreFailed = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

function dismiss() {
  if (timer) clearTimeout(timer)
  timer = null
  pending.value = null
  restoreFailed.value = false
}

const PATH = { log: 'logs', trip: 'trips' } as const

/**
 * Löschen von Ladevorgängen und Fahrten mit Rückgängig statt Bestätigungsdialog. Serverseitig
 * ist das Löschen ein Soft-Delete, der Restore-Endpoint macht es ungeschehen.
 */
export function useLogUndo() {
  async function remove(kind: PendingUndo['kind'], id: string, onChanged?: () => unknown) {
    await api.delete(`/${PATH[kind]}/${id}`)
    dismiss()
    pending.value = { id, kind, onChanged }
    timer = setTimeout(dismiss, UNDO_WINDOW_MS)
  }
  const deleteWithUndo = (id: string, onChanged?: () => unknown) => remove('log', id, onChanged)
  const deleteTripWithUndo = (id: string, onChanged?: () => unknown) => remove('trip', id, onChanged)

  async function undo() {
    const current = pending.value
    if (!current) return
    try {
      await api.post(`/${PATH[current.kind]}/${current.id}/restore`)
      dismiss()
      // Globaler Refresh für Ansichten, die beim Löschen schon geschlossen wurden (z. B. EditLogModal)
      useLogsRefreshStore().notifyLogSaved()
      await current.onChanged?.()
    } catch {
      // 409: Ladung - inzwischen gibt es eine aktive zur selben Zeit; Fahrt - inzwischen zusammengeführt
      restoreFailed.value = true
    }
  }

  return { pending, restoreFailed, deleteWithUndo, deleteTripWithUndo, undo, dismiss }
}

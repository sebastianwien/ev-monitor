import { ref } from 'vue'
import api from '../api/axios'
import { useLogsRefreshStore } from '../stores/logsRefresh'

const UNDO_WINDOW_MS = 10_000

interface PendingUndo {
  id: string
  onChanged?: () => unknown
}

// Modulweit: ein Toast in App.vue, egal aus welcher Ansicht gelöscht wurde.
const pending = ref<PendingUndo | null>(null)
const restoreFailed = ref(false)
// Zählt Löschen und Wiederherstellen, damit der Papierkorb-Zähler in der Liste mitläuft
const trashVersion = ref(0)
let timer: ReturnType<typeof setTimeout> | null = null

function dismiss() {
  if (timer) clearTimeout(timer)
  timer = null
  pending.value = null
  restoreFailed.value = false
}

/**
 * Löschen von Ladevorgängen mit Rückgängig statt Bestätigungsdialog. Serverseitig ist das
 * Löschen ein Soft-Delete, der Restore-Endpoint macht es ungeschehen.
 */
export function useLogUndo() {
  async function deleteWithUndo(id: string, onChanged?: () => unknown) {
    await api.delete(`/logs/${id}`)
    trashVersion.value++
    dismiss()
    pending.value = { id, onChanged }
    timer = setTimeout(dismiss, UNDO_WINDOW_MS)
  }

  async function undo() {
    const current = pending.value
    if (!current) return
    try {
      await api.post(`/logs/${current.id}/restore`)
      trashVersion.value++
      dismiss()
      // Globaler Refresh für Ansichten, die beim Löschen schon geschlossen wurden (z. B. EditLogModal)
      useLogsRefreshStore().notifyLogSaved()
      await current.onChanged?.()
    } catch {
      // 409: inzwischen gibt es einen aktiven Ladevorgang zur selben Zeit
      restoreFailed.value = true
    }
  }

  return { pending, restoreFailed, trashVersion, deleteWithUndo, undo, dismiss }
}

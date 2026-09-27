import { ref, type Ref } from 'vue'
import api from '../api/axios'
import { useLogsRefreshStore } from '../stores/logsRefresh'

export interface DeletedLog {
  id: string
  loggedAt: string
  kwhCharged: number | null
  kwhAtVehicle: number | null
  dataSource: string | null
  deletedAt: string
}

/**
 * Papierkorb eines Autos: gelöschte Ladevorgänge wiederherstellen oder endgültig entfernen.
 * Endgültig entfernt heißt: ein späterer Sync oder Upload darf den Vorgang wieder anlegen.
 * Geladen wird erst beim Öffnen, nicht mit der Ladeliste.
 */
export function useDeletedLogs(carId: Ref<string | null>) {
  const logs = ref<DeletedLog[]>([])
  /** Letzte fehlgeschlagene Aktion je Eintrag, damit die Zeile den passenden Text zeigt. */
  const failed = ref(new Map<string, 'restore' | 'purge'>())
  const busyId = ref<string | null>(null)
  const loading = ref(false)

  async function load() {
    if (!carId.value) { logs.value = []; return }
    loading.value = true
    try {
      const res = await api.get('/logs/deleted', { params: { carId: carId.value } })
      logs.value = res.data
    } catch {
      logs.value = []
    } finally {
      loading.value = false
    }
  }

  function markFailed(id: string, action: 'restore' | 'purge') {
    failed.value = new Map(failed.value).set(id, action)
  }

  function drop(id: string) {
    logs.value = logs.value.filter(l => l.id !== id)
  }

  async function restore(id: string) {
    busyId.value = id
    try {
      await api.post(`/logs/${id}/restore`)
      drop(id)
      useLogsRefreshStore().notifyLogSaved()
    } catch {
      // 409: zur selben Zeit gibt es inzwischen einen aktiven Ladevorgang
      markFailed(id, 'restore')
    } finally {
      busyId.value = null
    }
  }

  async function purge(id: string) {
    busyId.value = id
    try {
      await api.delete(`/logs/${id}/purge`)
      drop(id)
    } catch {
      markFailed(id, 'purge')
    } finally {
      busyId.value = null
    }
  }

  return { logs, failed, busyId, loading, load, restore, purge }
}

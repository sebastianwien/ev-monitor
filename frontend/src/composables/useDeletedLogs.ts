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
 */
export function useDeletedLogs(carId: Ref<string | null>) {
  const logs = ref<DeletedLog[]>([])
  const failedIds = ref(new Set<string>())
  const busyId = ref<string | null>(null)

  async function load() {
    if (!carId.value) { logs.value = []; return }
    try {
      const res = await api.get('/logs/deleted', { params: { carId: carId.value } })
      logs.value = res.data
    } catch {
      logs.value = []
    }
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
      failedIds.value = new Set([...failedIds.value, id])
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
      failedIds.value = new Set([...failedIds.value, id])
    } finally {
      busyId.value = null
    }
  }

  return { logs, failedIds, busyId, load, restore, purge }
}

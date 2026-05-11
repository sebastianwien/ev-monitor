import { onBeforeUnmount, ref } from 'vue'
import xpengService, { type XpengConnectionDto, type XpengJobDto } from '../api/xpengService'

const LS_KEY = 'xpeng:activeJobId'

/**
 * Encapsulates XPeng connection + job listing, active-job polling, and
 * localStorage-based resume after page reload.
 */
export function useXpengJobs() {
  const connections = ref<XpengConnectionDto[]>([])
  const jobs = ref<XpengJobDto[]>([])
  const activeJob = ref<XpengJobDto | null>(null)
  const loading = ref(true)
  const error = ref('')

  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function refresh() {
    loading.value = true
    error.value = ''
    try {
      const [c, j] = await Promise.all([
        xpengService.listConnections(),
        xpengService.listJobs(),
      ])
      connections.value = c
      jobs.value = j
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } }; message?: string }
      error.value = err.response?.data?.error ?? err.message ?? 'Unknown error'
    } finally {
      loading.value = false
    }
  }

  function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }

  function startPolling(jobId: string) {
    stopPolling()
    try { localStorage.setItem(LS_KEY, jobId) } catch { /* ignore quota */ }
    pollTimer = setInterval(async () => {
      try {
        const job = await xpengService.getJob(jobId)
        activeJob.value = job
        if (job.status === 'DONE' || job.status === 'FAILED') {
          stopPolling()
          try { localStorage.removeItem(LS_KEY) } catch { /* ignore */ }
          await refresh()
        }
      } catch (e) {
        console.error('XPeng job polling failed', e)
      }
    }, 3000)
  }

  async function tryResumeFromStorage() {
    let stored: string | null = null
    try { stored = localStorage.getItem(LS_KEY) } catch { /* ignore */ }
    if (!stored) return
    try {
      const job = await xpengService.getJob(stored)
      activeJob.value = job
      if (job.status === 'QUEUED' || job.status === 'PROCESSING') {
        startPolling(stored)
      } else {
        // Stale - already finished. Show last state, clear pointer.
        try { localStorage.removeItem(LS_KEY) } catch { /* ignore */ }
      }
    } catch {
      // Job no longer exists (deleted, foreign user, etc.) - clean up.
      try { localStorage.removeItem(LS_KEY) } catch { /* ignore */ }
    }
  }

  onBeforeUnmount(() => stopPolling())

  return {
    connections, jobs, activeJob, loading, error,
    refresh, startPolling, stopPolling, tryResumeFromStorage,
  }
}

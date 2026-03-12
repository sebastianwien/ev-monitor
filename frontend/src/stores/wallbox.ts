import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import goeService, { type GoeConnection } from '@/api/goeService'

const DEMO_CONNECTION: GoeConnection = {
  id: 'demo',
  serial: 'DEMO000001',
  displayName: 'Wallbox',
  active: true,
  carState: 2,
  lastPollError: null,
  carStateLabel: 'Lädt',
  tariffCentsPerKwh: 28,
  geohash: null,
}

const POLL_CHARGING_MS = 2 * 60 * 1000
const POLL_IDLE_MS     = 4 * 60 * 1000

export const useWallboxStore = defineStore('wallbox', () => {
  const connections = ref<GoeConnection[]>([])
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let isDemoMode = false

  const isCharging = computed(() => connections.value.some(c => c.carState === 2))
  const hasConnections = computed(() => connections.value.length > 0)

  // The most relevant connection to show in the navbar chip:
  // prefer charging → error → any
  const activeConnection = computed(() => {
    const charging = connections.value.find(c => c.carState === 2)
    if (charging) return charging
    const errored = connections.value.find(c => c.carState === 5 || c.lastPollError)
    if (errored) return errored
    return connections.value[0] ?? null
  })

  async function fetchConnections() {
    if (isDemoMode) return
    try {
      connections.value = await goeService.getConnections()
    } catch { /* ignore — chip simply stays hidden */ }
    schedulePoll()
  }

  function schedulePoll() {
    if (pollTimer) clearTimeout(pollTimer)
    if (document.hidden || isDemoMode) return
    pollTimer = setTimeout(fetchConnections, isCharging.value ? POLL_CHARGING_MS : POLL_IDLE_MS)
  }

  function onVisibilityChange() {
    if (document.hidden) {
      if (pollTimer) clearTimeout(pollTimer)
    } else {
      fetchConnections()
    }
  }

  function init(isDemo: boolean) {
    isDemoMode = isDemo
    if (isDemo) {
      connections.value = [DEMO_CONNECTION]
      return
    }
    fetchConnections()
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  function reset() {
    if (pollTimer) clearTimeout(pollTimer)
    document.removeEventListener('visibilitychange', onVisibilityChange)
    connections.value = []
    isDemoMode = false
  }

  // Called by GoeStatusCard/GoeIntegration after connect/disconnect to refresh
  async function refresh() {
    if (!isDemoMode) await fetchConnections()
  }

  return { connections, isCharging, hasConnections, activeConnection, init, reset, refresh }
})

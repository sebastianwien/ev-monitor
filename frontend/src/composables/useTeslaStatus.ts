import { ref, onUnmounted } from 'vue'
import teslaFleetService, { type TeslaConnectionStatus } from '../api/teslaFleetService'
import { useAuthStore } from '../stores/auth'

const INTERVAL_ACTIVE_MS  = 2 * 60 * 1000  // 2 min when online or charging
const INTERVAL_IDLE_MS    = 5 * 60 * 1000  // 5 min when asleep or unknown

/**
 * Fetches and adaptively polls Tesla vehicle status.
 * Only activates when the user has at least one Tesla in their car list.
 * Polling interval: 2 min when online/charging, 5 min otherwise.
 * All calls go to our backend only — no direct Tesla API calls from frontend.
 */
const DEMO_STATUS: TeslaConnectionStatus = {
  connected: true,
  vehicleName: 'Tesla Model 3',
  carId: null,  // matched by brand name in template
  lastSyncAt: null,
  autoImportEnabled: true,
  geocodingInProgress: false,
  vehicleState: 'charging',
}

export function useTeslaStatus() {
  const authStore = useAuthStore()
  const teslaStatus = ref<TeslaConnectionStatus | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  const isActive = () =>
    teslaStatus.value?.vehicleState === 'charging' ||
    teslaStatus.value?.vehicleState === 'online'

  const poll = async () => {
    try {
      teslaStatus.value = await teslaFleetService.getStatus()
    } catch {
      // not connected or no Tesla — leave as null
    }
    schedule()
  }

  const schedule = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(poll, isActive() ? INTERVAL_ACTIVE_MS : INTERVAL_IDLE_MS)
  }

  const start = async (hasTesla: boolean) => {
    if (!hasTesla) return
    if (authStore.isDemoAccount) {
      teslaStatus.value = DEMO_STATUS
      return  // no polling, no backend call
    }
    await poll()   // immediate first fetch, then schedule
  }

  const stop = () => {
    if (timer) { clearTimeout(timer); timer = null }
  }

  onUnmounted(stop)

  return { teslaStatus, start, stop }
}

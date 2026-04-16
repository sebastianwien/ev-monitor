import { ref, onUnmounted } from 'vue'
import smartcarService, { type SmartcarConnectionStatus } from '../api/smartcarService'

const INTERVAL_ACTIVE_MS = 2 * 60 * 1000  // 2 min when charging
const INTERVAL_IDLE_MS   = 5 * 60 * 1000  // 5 min otherwise

/**
 * Fetches and adaptively polls Smartcar vehicle status.
 * Only activates when the user has at least one car (we can't know upfront which
 * cars have Smartcar connected, so we always try if there are any cars).
 * Polling interval: 2 min when charging, 5 min otherwise.
 */
export function useSmartcarStatus() {
  const smartcarStatus = ref<SmartcarConnectionStatus | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  const isCharging = () => smartcarStatus.value?.vehicleState === 'CHARGING'

  const poll = async () => {
    try {
      smartcarStatus.value = await smartcarService.getStatus()
    } catch {
      // not connected or error — leave as null
    }
    schedule()
  }

  const schedule = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(poll, isCharging() ? INTERVAL_ACTIVE_MS : INTERVAL_IDLE_MS)
  }

  const start = async (hasAnyCars: boolean) => {
    if (!hasAnyCars) return
    await poll()
  }

  const stop = () => {
    if (timer) { clearTimeout(timer); timer = null }
  }

  onUnmounted(stop)

  return { smartcarStatus, start, stop }
}

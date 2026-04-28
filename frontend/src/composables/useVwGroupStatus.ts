import { ref, onUnmounted } from 'vue'
import vwGroupService, { type VwGroupConnectionStatus } from '../api/vwGroupService'

const INTERVAL_CHARGING_MS = 30 * 1000
const INTERVAL_IDLE_MS     = 60 * 1000

export function useVwGroupStatus() {
  const vwGroupStatus = ref<VwGroupConnectionStatus | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null
  let activeBrand: string | null = null

  const isCharging = () => vwGroupStatus.value?.vehicleState === 'charging'

  const poll = async () => {
    if (!activeBrand) return
    try {
      const status = await vwGroupService.getStatus(activeBrand)
      if (status.connected) {
        vwGroupStatus.value = status
      } else {
        vwGroupStatus.value = null
      }
    } catch {
      // not connected or error
    }
    schedule()
  }

  const schedule = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(poll, isCharging() ? INTERVAL_CHARGING_MS : INTERVAL_IDLE_MS)
  }

  const start = async (brand: string | null) => {
    if (!brand) return
    activeBrand = brand
    await poll()
  }

  const stop = () => {
    if (timer) { clearTimeout(timer); timer = null }
    activeBrand = null
  }

  onUnmounted(stop)

  return { vwGroupStatus, start, stop }
}

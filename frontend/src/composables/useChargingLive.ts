import { ref, watchEffect, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import axios from 'axios'

export interface LiveChargingData {
  isActive: boolean
  chargingType: string | null
  powerKw: number | null
  socPercent: number | null
  energyRemainingKwh: number | null
  timeToFullMinutes: number | null
  estRangeKm: number | null
  chargeAmps: number | null
  sessionStartedAt: string | null
  socAtSessionStart: number | null
  chargeLimitSoc: number | null
  lastUpdatedAt: string | null
}

interface PowerHistoryPoint {
  ts: number
  kw: number
}

const HISTORY_MAX_POINTS = 120
const HISTORY_WINDOW_MIN = 30
// Aktive Session: schnell, damit die Kurve fluessig waechst.
// Idle (keine Session): seltener, weil >99% der Zeit nichts zu tun ist.
const POLL_INTERVAL_ACTIVE_MS = 10_000
const POLL_INTERVAL_IDLE_MS = 60_000

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  let activeController: AbortController | undefined

  function isPageVisible(): boolean {
    return typeof document === 'undefined' || document.visibilityState !== 'hidden'
  }

  function nextDelay(): number {
    return data.value?.isActive ? POLL_INTERVAL_ACTIVE_MS : POLL_INTERVAL_IDLE_MS
  }

  function appendPowerPoint(kw: number | null, lastUpdatedAt: string | null) {
    if (kw == null) return
    const ts = lastUpdatedAt ? new Date(lastUpdatedAt).getTime() : Date.now()
    const last = powerHistory.value[powerHistory.value.length - 1]
    if (last && last.ts === ts) return
    powerHistory.value = [...powerHistory.value, { ts, kw }].slice(-HISTORY_MAX_POINTS)
  }

  async function fetchHistory(id: string, signal: AbortSignal) {
    try {
      const res = await axios.get<{ points: { timestamp: string, powerKw: number }[] }>(
        `/api/cars/${id}/live/charging/history?minutes=${HISTORY_WINDOW_MIN}`,
        { signal },
      )
      if (signal.aborted) return
      powerHistory.value = res.data.points.map(p => ({
        ts: new Date(p.timestamp).getTime(),
        kw: p.powerKw,
      })).slice(-HISTORY_MAX_POINTS)
    } catch {
      // best-effort - realtime-Ticks fuellen den Buffer
    }
  }

  async function pollLive(id: string, signal: AbortSignal) {
    try {
      const res = await axios.get<LiveChargingData>(
        `/api/cars/${id}/live/charging`,
        { signal },
      )
      if (signal.aborted) return
      data.value = res.data
      if (res.data.isActive) appendPowerPoint(res.data.powerKw, res.data.lastUpdatedAt)
      else powerHistory.value = []
    } catch {
      // Naechster Tick versucht Recovery
    }
  }

  function scheduleNext(id: string, controller: AbortController) {
    clearTimeout(timeoutId)
    if (!isPageVisible()) return   // Hidden Tab pausiert - visibilitychange weckt
    timeoutId = setTimeout(async () => {
      await pollLive(id, controller.signal)
      if (controller.signal.aborted) return
      scheduleNext(id, controller)
    }, nextDelay())
  }

  function refresh() {
    const id = carId.value
    if (!id || !activeController) return
    pollLive(id, activeController.signal)
  }

  function onVisibilityChange() {
    const id = carId.value
    const controller = activeController
    if (!id || !controller) return
    if (isPageVisible()) {
      // Beim Zurueckkommen sofort einholen + Zyklus neu starten
      pollLive(id, controller.signal).then(() => {
        if (!controller.signal.aborted) scheduleNext(id, controller)
      })
    } else {
      clearTimeout(timeoutId)
    }
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  watchEffect(() => {
    activeController?.abort()
    clearTimeout(timeoutId)
    powerHistory.value = []

    const id = carId.value
    if (!id) return

    const controller = new AbortController()
    activeController = controller

    fetchHistory(id, controller.signal)
      .then(() => pollLive(id, controller.signal))
      .then(() => {
        if (!controller.signal.aborted) scheduleNext(id, controller)
      })
  })

  onUnmounted(() => {
    activeController?.abort()
    clearTimeout(timeoutId)
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })

  return { data, powerHistory, refresh }
}

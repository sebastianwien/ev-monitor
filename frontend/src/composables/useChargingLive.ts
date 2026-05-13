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
  ts: number   // ms timestamp
  kw: number   // momentane Ladeleistung
}

const HISTORY_MAX_POINTS = 120     // ~30 Minuten Backend-History + Realtime-Buffer
const HISTORY_WINDOW_MIN = 30      // initial vom Backend geladen
const POLL_INTERVAL_MS = 5000

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  let intervalId: ReturnType<typeof setInterval> | undefined
  let activeController: AbortController | undefined

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
      // best-effort - bei Fehler bleibt der Buffer leer, realtime-Ticks fuellen ihn
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
      // Polling laeuft weiter, naechster Tick versucht Recovery
    }
  }

  function refresh() {
    const id = carId.value
    if (!id || !activeController) return
    pollLive(id, activeController.signal)
  }

  watchEffect(() => {
    // Vorherige Iteration sauber abreissen: pending Requests verwerfen + Interval stoppen
    activeController?.abort()
    clearInterval(intervalId)
    powerHistory.value = []

    const id = carId.value
    if (!id) return

    const controller = new AbortController()
    activeController = controller

    fetchHistory(id, controller.signal)
      .then(() => pollLive(id, controller.signal))

    intervalId = setInterval(() => pollLive(id, controller.signal), POLL_INTERVAL_MS)
  })

  onUnmounted(() => {
    activeController?.abort()
    clearInterval(intervalId)
  })

  return { data, powerHistory, refresh }
}

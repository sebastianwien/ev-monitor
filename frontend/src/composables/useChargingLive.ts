import { ref, watch, watchEffect, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'
// IMPORTANT: 'api' aus '../api/axios' importieren (nicht raw axios!) - sonst
// fehlt der Authorization-Header und das Backend antwortet mit 401, obwohl der
// User eingeloggt ist. baseURL ist auf '/api' gesetzt, also URLs ohne /api-Prefix.
import api from '../api/axios'

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
// Aktive Session: schnell, damit die Kurve fluessig waechst.
// Idle (keine Session): seltener, weil >99% der Zeit nichts zu tun ist.
const POLL_INTERVAL_ACTIVE_MS = 10_000
const POLL_INTERVAL_IDLE_MS = 60_000

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  // Letzter Polling-Fehler, exposed fuer UI-Telemetrie. catch{}-Bloecke
  // setzen den Wert statt zu schlucken, damit ein dauerhafter Backend-Outage
  // sichtbar gemacht werden kann (LiveChargingCard rendert dann z.B. eine
  // hilfreiche Fehlermeldung statt nur den Stale-Indikator).
  const error = ref<string | null>(null)
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

  async function fetchHistory(id: string, sessionStartIso: string, signal: AbortSignal) {
    try {
      const toIso = new Date().toISOString()
      const res = await api.get<{ points: { timestamp: string, powerKw: number }[] }>(
        `/cars/${id}/live/charging/history?from=${encodeURIComponent(sessionStartIso)}&to=${encodeURIComponent(toIso)}`,
        { signal },
      )
      if (signal.aborted) return
      powerHistory.value = res.data.points.map(p => ({
        ts: new Date(p.timestamp).getTime(),
        kw: p.powerKw,
      })).slice(-HISTORY_MAX_POINTS)
      error.value = null
    } catch (e) {
      if (signal.aborted) return
      // best-effort - realtime-Ticks fuellen den Buffer; History-Outage soll die
      // Live-Polls nicht blockieren, also nur leise melden.
      error.value = e instanceof Error ? e.message : 'history_fetch_failed'
    }
  }

  async function pollLive(id: string, signal: AbortSignal) {
    try {
      const res = await api.get<LiveChargingData>(
        `/cars/${id}/live/charging`,
        { signal },
      )
      if (signal.aborted) return
      data.value = res.data
      if (res.data.isActive) {
        appendPowerPoint(res.data.powerKw, res.data.lastUpdatedAt)
        // Defense-in-Depth: erstes Tick mit aktiver Session - History direkt ziehen.
        // Der watch unten loest das normalerweise via sessionStartedAt-delta aus,
        // aber bei page-mount mit bereits laufender Session ist die Subscription-
        // reihenfolge fragil. Doppel-fetch ist harmlos (idempotente Liste).
        if (powerHistory.value.length <= 1 && res.data.sessionStartedAt) {
          fetchHistory(id, res.data.sessionStartedAt, signal)
        }
      } else {
        powerHistory.value = []
      }
      error.value = null
    } catch (e) {
      if (signal.aborted) return
      // Naechster Tick versucht Recovery - aber Fehler bleibt expose-bar, damit
      // die UI im Dauerausfall-Fall mehr als nur den Stale-Indikator zeigen kann.
      error.value = e instanceof Error ? e.message : 'live_fetch_failed'
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

  onMounted(() => {
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibilityChange)
    }
  })

  watchEffect(() => {
    activeController?.abort()
    clearTimeout(timeoutId)
    powerHistory.value = []
    data.value = null

    const id = carId.value
    if (!id) return

    const controller = new AbortController()
    activeController = controller

    // Erst pollLive: liefert isActive + sessionStartedAt. History wird dann durch
    // den watch unten ausgeloest, sobald sessionStartedAt bekannt ist.
    pollLive(id, controller.signal)
      .then(() => {
        if (!controller.signal.aborted) scheduleNext(id, controller)
      })
  })

  // Sobald wir eine neue Session sehen (auch ohne carId-Wechsel - z.B. User
  // steckt das Auto neu an waehrend Dashboard offen ist), die volle Kurve
  // ab Session-Start vom Backend ziehen.
  watch(() => data.value?.sessionStartedAt ?? null, (sessionStart) => {
    const id = carId.value
    const controller = activeController
    if (!id || !controller || !sessionStart) return
    fetchHistory(id, sessionStart, controller.signal)
  })

  onUnmounted(() => {
    activeController?.abort()
    clearTimeout(timeoutId)
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })

  return { data, powerHistory, error, refresh }
}

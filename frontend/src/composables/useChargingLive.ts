import { ref, computed, watchEffect, onMounted, onUnmounted } from 'vue'
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
  sessionEndedAt: string | null    // nur in 30-min-Grace-Period nach Session-Ende gesetzt
  socAtSessionStart: number | null
  chargeLimitSoc: number | null
  lastUpdatedAt: string | null
  energyAddedKwh: number | null    // nur Quellen mit Energiezaehler (Smartcar je nach Marke)
  updateIntervalSeconds: number    // Takt der Quelle: Tesla 30, Polestar 120, Smartcar 300
}

interface PowerHistoryPoint {
  ts: number
  kw: number
}

interface SocHistoryPoint {
  ts: number
  soc: number
}

interface HistoryResponse {
  points: { timestamp: string, powerKw: number | null, socPercent: number | null }[]
}

// Defensiver Cap auf die Server-History (downsampled auf <=120 Bins). Server ist
// Source of Truth; dieser slice schuetzt nur gegen eine unerwartet grosse Response.
const HISTORY_MAX_POINTS = 120
// Aktive Session: ein Drittel des Quellen-Takts, zwischen 10 s (Tesla) und 60 s
// (Smartcar), damit die Kurve fluessig waechst, ohne langsame Quellen zu hammern.
// Idle (keine Session): seltener, weil >99% der Zeit nichts zu tun ist.
const POLL_INTERVAL_ACTIVE_MIN_MS = 10_000
const POLL_INTERVAL_ACTIVE_MAX_MS = 60_000
const POLL_INTERVAL_IDLE_MS = 60_000
// Server ohne Takt (aelterer Connector): Tesla-Takt annehmen.
const DEFAULT_UPDATE_INTERVAL_S = 30

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  const socHistory = ref<SocHistoryPoint[]>([])
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

  const updateIntervalSeconds = computed(() => data.value?.updateIntervalSeconds || DEFAULT_UPDATE_INTERVAL_S)

  function nextDelay(): number {
    if (!data.value?.isActive) return POLL_INTERVAL_IDLE_MS
    const third = updateIntervalSeconds.value * 1000 / 3
    return Math.min(POLL_INTERVAL_ACTIVE_MAX_MS, Math.max(POLL_INTERVAL_ACTIVE_MIN_MS, third))
  }

  async function fetchHistory(id: string, sessionStartIso: string, signal: AbortSignal) {
    try {
      const toIso = new Date().toISOString()
      const res = await api.get<HistoryResponse>(
        `/cars/${id}/live/charging/history?from=${encodeURIComponent(sessionStartIso)}&to=${encodeURIComponent(toIso)}`,
        { signal },
      )
      if (signal.aborted) return
      const points = res.data.points.slice(-HISTORY_MAX_POINTS)
      powerHistory.value = points
        .filter(p => p.powerKw != null)
        .map(p => ({ ts: new Date(p.timestamp).getTime(), kw: p.powerKw as number }))
      socHistory.value = points
        .filter(p => p.socPercent != null)
        .map(p => ({ ts: new Date(p.timestamp).getTime(), soc: p.socPercent as number }))
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
      // Grace-Period: Backend liefert sessionStartedAt auch nach Session-Ende fuer
      // 30 Min weiter (isActive=false, sessionEndedAt gesetzt) - in diesem Fenster
      // ziehen wir die fertige Kurve weiter nach.
      const hasSession = !!res.data.sessionStartedAt
      if (hasSession && res.data.sessionStartedAt) {
        // Die volle Session-Kurve wird bei JEDEM Poll frisch vom Connector geholt
        // (server-seitig begrenzt, Quelle je nach Auto: Tesla, Polestar oder Smartcar).
        // Bewusst KEIN clientseitiges Mitschreiben: ein lokaler Append-Buffer wuerde
        // nach ~20 Min den Session-Start aus dem 120-Punkte-Fenster rotieren - bei DC
        // genau die interessante Peak-Rampe. Server bleibt Source of Truth.
        fetchHistory(id, res.data.sessionStartedAt, signal)
      } else {
        powerHistory.value = []
        socHistory.value = []
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
    socHistory.value = []
    data.value = null

    const id = carId.value
    if (!id) return

    const controller = new AbortController()
    activeController = controller

    pollLive(id, controller.signal)
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

  // Eine laufende Ladung, egal ob AC oder DC. Einzige Regel fuer "Live sichtbar"
  // (Dashboard-Card und Story-Hero).
  const isLive = computed(() => !!data.value?.isActive)

  return { data, isLive, powerHistory, socHistory, updateIntervalSeconds, error, refresh }
}

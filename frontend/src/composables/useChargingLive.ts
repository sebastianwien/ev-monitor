import { ref, watch, watchEffect, onUnmounted } from 'vue'
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
// Aktive Session: schnell, damit die Kurve fluessig waechst.
// Idle (keine Session): seltener, weil >99% der Zeit nichts zu tun ist.
const POLL_INTERVAL_ACTIVE_MS = 10_000
const POLL_INTERVAL_IDLE_MS = 60_000

// ── Mock-Mode fuer lokales Mobile-Testing (kein Backend, keine Tesla noetig) ──
// Aktivierbar mit `?mock=charging` (oder ?mock=1/?mock) in Vite-DEV-Builds.
// Production-Builds (import.meta.env.DEV=false) ignorieren das Query-Flag.
function isMockMode(): boolean {
  if (!import.meta.env.DEV) return false
  if (typeof window === 'undefined') return false
  const v = new URLSearchParams(window.location.search).get('mock')
  return v !== null && v !== 'false' && v !== '0'
}

const MOCK_SOC_START = 8
const MOCK_SOC_TARGET = 80
const MOCK_BATTERY_KWH = 75   // Tesla M3 LR nominal

const MOCK_PHASES = [
  { rate: 5.0, end: 20 },
  { rate: 3.0, end: 35 },
  { rate: 2.0, end: 50 },
  { rate: 1.5, end: 70 },
  { rate: 1.0, end: 80 },
  { rate: 0.6, end: 90 },
  { rate: 0.4, end: 100 },
]

function socAtMinutesElapsed(minutes: number): number {
  let soc = MOCK_SOC_START
  let remaining = Math.max(0, minutes)
  for (const p of MOCK_PHASES) {
    if (soc >= p.end) continue
    const need = (p.end - soc) / p.rate
    if (remaining < need) { soc += remaining * p.rate; return Math.min(100, soc) }
    soc = p.end
    remaining -= need
  }
  return 100
}

function minutesToReachSoc(currentSoc: number, targetSoc: number): number {
  if (currentSoc >= targetSoc) return 0
  let soc = currentSoc
  let minutes = 0
  for (const p of MOCK_PHASES) {
    if (soc >= p.end) continue
    const upper = Math.min(p.end, targetSoc)
    minutes += (upper - soc) / p.rate
    soc = upper
    if (soc >= targetSoc) break
  }
  return Math.max(1, Math.round(minutes))
}

// Realistische Tesla-M3-LR-AWD-Kurve an Ionity 350 kW: Peak ~188 kW bei 15-25%,
// dann zuegiger Abfall, finaler langer Tail auf ~13 kW bei 97%. Modelliert nach
// einer publizierten Referenzmessung; soll der Lade-Realitaet entsprechen, nicht
// der Tesla-Marketing-Behauptung "250 kW Peak" (die nur bei sehr kalter Batterie
// und nahe 0 % SoC am V3-Supercharger erreichbar ist).
function mockPowerKwForSoc(soc: number): number {
  if (soc < 5)  return 40 + soc * 16          // 40 -> 120 (Ramp-up)
  if (soc < 15) return 120 + (soc - 5) * 6.8  // 120 -> 188 (Anstieg zum Peak)
  if (soc < 25) return 188                    // Peak-Plateau
  if (soc < 35) return 188 - (soc - 25) * 4.5 // 188 -> 143 (erster Abfall)
  if (soc < 45) return 143 - (soc - 35) * 3.5 // 143 -> 108
  if (soc < 60) return 108 - (soc - 45) * 0.7 // 108 -> 98 (Plateau-aehnlich)
  if (soc < 80) return 98  - (soc - 60) * 2.4 // 98 -> 50
  if (soc < 90) return 50  - (soc - 80) * 2.5 // 50 -> 25
  return Math.max(10, 25 - (soc - 90) * 1.2)  // 25 -> ~13
}

// Skaliert die rohe Power-Kurve so, dass das Zeit-Integral der Kurve ueber die
// volle SoC-Spanne exakt dem SoC-Delta x Akku-Kapazitaet entspricht. Damit ist
// die "+km nachgeladen"-Anzeige (Integration der kW-Punkte / Verbrauch) und der
// REICHWEITE-Wert (BMS-Schaetzung aus SoC) im Mock konsistent.
const MOCK_KW_SCALE = (() => {
  const totalMinutes = minutesToReachSoc(MOCK_SOC_START, 100)
  const targetKwh = (100 - MOCK_SOC_START) * MOCK_BATTERY_KWH / 100
  let rawKwh = 0
  const step = 0.05 // Minuten - feines Sampling fuer akkurate Naeherung
  for (let t = 0; t < totalMinutes; t += step) {
    const soc = socAtMinutesElapsed(t)
    rawKwh += mockPowerKwForSoc(soc) * (step / 60)
  }
  return rawKwh > 0 ? targetKwh / rawKwh : 1
})()

function scaledMockPowerKw(soc: number): number {
  return mockPowerKwForSoc(soc) * MOCK_KW_SCALE
}

function buildMockData(sessionStart: Date): LiveChargingData {
  let minutesElapsed = (Date.now() - sessionStart.getTime()) / 60000
  const totalMinutes = minutesToReachSoc(MOCK_SOC_START, 100)
  if (minutesElapsed > totalMinutes) minutesElapsed = minutesElapsed % totalMinutes

  const soc = socAtMinutesElapsed(minutesElapsed)
  let powerKw = scaledMockPowerKw(soc) + (Math.random() - 0.5) * 2.5
  if (powerKw < 0) powerKw = 0

  const chargeAmps = Math.round(powerKw * 2.5)
  // Konsistent mit dem ge-koppelten kWh-Modell: Reichweite = aktuelle Restenergie /
  // Annahme-Verbrauch. Annahme 16 kWh/100km (= PowerCurveChart-Fallback) bedeutet
  // 100 % Akku ≙ 469 km - damit matched die nachgeladene Reichweite der BMS-Schaetzung
  // bis auf den Start-SoC-Sockel.
  const MOCK_REF_CONSUMPTION = 16
  const energyRemainingKwh = +(MOCK_BATTERY_KWH * soc / 100).toFixed(1)
  const estRangeKm = Math.round(energyRemainingKwh * 100 / MOCK_REF_CONSUMPTION)
  const timeToFullMinutes = minutesToReachSoc(soc, MOCK_SOC_TARGET)

  return {
    isActive: true,
    chargingType: 'DC',
    powerKw: +powerKw.toFixed(1),
    socPercent: +soc.toFixed(1),
    energyRemainingKwh,
    timeToFullMinutes,
    estRangeKm,
    chargeAmps,
    sessionStartedAt: sessionStart.toISOString(),
    socAtSessionStart: MOCK_SOC_START,
    chargeLimitSoc: MOCK_SOC_TARGET,
    lastUpdatedAt: new Date().toISOString(),
  }
}

function seedMockHistory(sessionStart: Date): PowerHistoryPoint[] {
  const points: PowerHistoryPoint[] = []
  const totalMinutes = minutesToReachSoc(MOCK_SOC_START, 100)
  let minutesElapsed = Math.max(1, (Date.now() - sessionStart.getTime()) / 60000)
  if (minutesElapsed > totalMinutes) minutesElapsed = minutesElapsed % totalMinutes
  const N = 30
  for (let i = 0; i < N; i++) {
    const frac = i / (N - 1)
    const t = frac * minutesElapsed
    const soc = socAtMinutesElapsed(t)
    const kw = Math.max(0, scaledMockPowerKw(soc) + (Math.random() - 0.5) * 3.0)
    const ts = sessionStart.getTime() + t * 60000
    points.push({ ts, kw: +kw.toFixed(1) })
  }
  return points
}

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  let activeController: AbortController | undefined

  const mockMode = isMockMode()
  // Mock-Session laeuft schon ~33 Min → SoC ~75 % bei Target 80 %, also "kurz
  // vor Ende" (ETA ~5 Min). Die Kurve enthaelt den vollen Verlauf vom Ramp-up
  // durchs Peak-Plateau bis in den Tail, sodass das UI-Testing alle Bereiche der
  // Y-Achse abdeckt.
  const mockSessionStart = mockMode ? new Date(Date.now() - 33 * 60 * 1000) : null

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
    if (mockMode && mockSessionStart) {
      if (signal.aborted) return
      powerHistory.value = seedMockHistory(mockSessionStart)
      return
    }
    try {
      const toIso = new Date().toISOString()
      const res = await axios.get<{ points: { timestamp: string, powerKw: number }[] }>(
        `/api/cars/${id}/live/charging/history?from=${encodeURIComponent(sessionStartIso)}&to=${encodeURIComponent(toIso)}`,
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
    if (mockMode && mockSessionStart) {
      if (signal.aborted) return
      const d = buildMockData(mockSessionStart)
      data.value = d
      // Erstes Tick: volle Mock-Historie seeden. Folge-Ticks: einzelnen Punkt
      // anhaengen, damit die Kurve sichtbar "weiterlaeuft". Robust gegen
      // Reihenfolge-Schwankungen zwischen watchEffect und watch-immediate, die
      // sonst nur den letzten Punkt durchreichen.
      if (powerHistory.value.length === 0) {
        powerHistory.value = seedMockHistory(mockSessionStart)
      } else {
        appendPowerPoint(d.powerKw, d.lastUpdatedAt)
      }
      return
    }
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
  // ab Session-Start vom Backend ziehen. immediate=true ist wichtig fuer
  // Mock-Mode: dort setzt pollLive() data.value synchron, der watch ohne
  // immediate sieht das schon als initialen Wert und fired nie.
  watch(() => data.value?.sessionStartedAt ?? null, (sessionStart) => {
    const id = carId.value
    const controller = activeController
    if (!id || !controller || !sessionStart) return
    fetchHistory(id, sessionStart, controller.signal)
  }, { immediate: true })

  onUnmounted(() => {
    activeController?.abort()
    clearTimeout(timeoutId)
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })

  return { data, powerHistory, refresh }
}

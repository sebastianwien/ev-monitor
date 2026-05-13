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

export interface PowerHistoryPoint {
  ts: number     // ms timestamp
  kw: number     // momentane Ladeleistung
}

const HISTORY_MAX_POINTS = 120   // ~30 Minuten Backend-History + Realtime-Buffer

/**
 * Returns true when the current URL has `?mock=charging` (or `?mock=1`/`?mock`)
 * AND we're in a Vite dev build. Production builds never mock.
 */
function isMockMode(): boolean {
  if (!import.meta.env.DEV) return false
  if (typeof window === 'undefined') return false
  const v = new URLSearchParams(window.location.search).get('mock')
  return v !== null && v !== 'false' && v !== '0'
}

const MOCK_SOC_START = 8
const MOCK_SOC_TARGET = 80

/**
 * SoC als Funktion der seit Session-Start verstrichenen Minuten.
 * Stueckweise mit phasenabhaengigen %/min Raten - schneller bei niedrigem SoC,
 * langsamer am Ende. Bei einer 22-Min-Session landet man bei ~57% SoC, sodass
 * die Kurve den Peak und einen Teil des Abfalls zeigt.
 */
function socAtMinutesElapsed(minutes: number): number {
  let soc = MOCK_SOC_START
  let remaining = Math.max(0, minutes)
  const phases = [
    { rate: 5.0, end: 20 },
    { rate: 3.0, end: 35 },
    { rate: 2.0, end: 50 },
    { rate: 1.5, end: 70 },
    { rate: 1.0, end: 80 },
    { rate: 0.6, end: 90 },
    { rate: 0.4, end: 100 },
  ]
  for (const p of phases) {
    if (soc >= p.end) continue
    const need = (p.end - soc) / p.rate
    if (remaining < need) { soc += remaining * p.rate; return Math.min(100, soc) }
    soc = p.end
    remaining -= need
  }
  return 100
}

/** Minuten bis ein Ziel-SoC ausgehend vom aktuellen erreicht wird. */
function minutesToReachSoc(currentSoc: number, targetSoc: number): number {
  if (currentSoc >= targetSoc) return 0
  let soc = currentSoc
  let minutes = 0
  const phases = [
    { rate: 5.0, end: 20 },
    { rate: 3.0, end: 35 },
    { rate: 2.0, end: 50 },
    { rate: 1.5, end: 70 },
    { rate: 1.0, end: 80 },
    { rate: 0.6, end: 90 },
    { rate: 0.4, end: 100 },
  ]
  for (const p of phases) {
    if (soc >= p.end) continue
    const upper = Math.min(p.end, targetSoc)
    minutes += (upper - soc) / p.rate
    soc = upper
    if (soc >= targetSoc) break
  }
  return Math.max(1, Math.round(minutes))
}

/**
 * Erzeugt eine realistische Live-Session, die mit jedem Tick weiter fortschreitet.
 * SoC und Leistung folgen der Funktion oben. Wenn 100% erreicht sind, springt die
 * Session zurueck auf MOCK_SOC_START - das Tooling laeuft unendlich.
 */
function buildMockData(sessionStart: Date): LiveChargingData {
  let minutesElapsed = (Date.now() - sessionStart.getTime()) / 60000
  // Wenn die Session laenger als die volle Lade-Dauer dauert, modulo loopen.
  const totalMinutes = minutesToReachSoc(MOCK_SOC_START, 100)
  if (minutesElapsed > totalMinutes) minutesElapsed = minutesElapsed % totalMinutes

  const soc = socAtMinutesElapsed(minutesElapsed)
  let powerKw = mockPowerKwForSoc(soc) + (Math.random() - 0.5) * 2.5
  if (powerKw < 0) powerKw = 0

  // Strom: I = P / U, angenommen 400V DC -> Faktor ~2.5 fuer A aus kW
  const chargeAmps = Math.round(powerKw * 2.5)
  // Reichweite proportional zur Energie im Akku (Annahme: 57.5 kWh @ 100% = 341 km)
  const estRangeKm = Math.round(341 * (soc / 100))
  const energyRemainingKwh = +(57.5 * (soc / 100)).toFixed(1)
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

/**
 * Synthetische DC-Power-Kurve, modelliert nach einem Tesla-V3-Supercharger:
 * Schneller Ramp-up bis 250 kW Peak, kurze Plateau-Phase, ueber mehrere Stufen
 * abfallend bis ~30 kW gegen Ende.
 */
function mockPowerKwForSoc(soc: number): number {
  if (soc < 5)  return 40 + soc * 42           // 40 -> 250 (steiler Ramp-up)
  if (soc < 20) return 250                     // Peak-Plateau
  if (soc < 35) return 250 - (soc - 20) * 4.67 // 250 -> 180 zuegig
  if (soc < 50) return 180 - (soc - 35) * 4.0  // 180 -> 120
  if (soc < 70) return 120 - (soc - 50) * 2.0  // 120 -> 80
  if (soc < 80) return 80 - (soc - 70) * 2.0   // 80 -> 60
  if (soc < 90) return 60 - (soc - 80) * 2.0   // 60 -> 40
  return Math.max(30, 40 - (soc - 90))         // 40 -> 30
}

/**
 * Erzeugt 30 synthetische Power-Punkte ueber die seit Session-Start
 * verstrichene Zeit. SoC und Power folgen denselben Funktionen wie der Live-Tick,
 * sodass die Kurve in die aktuelle Live-Power-Anzeige nahtlos uebergeht.
 */
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
    const kw = Math.max(0, mockPowerKwForSoc(soc) + (Math.random() - 0.5) * 3.0)
    const ts = sessionStart.getTime() + t * 60000
    points.push({ ts, kw: +kw.toFixed(1) })
  }
  return points
}

export function useChargingLive(carId: Ref<string | null>) {
  const data = ref<LiveChargingData | null>(null)
  const powerHistory = ref<PowerHistoryPoint[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let intervalId: ReturnType<typeof setInterval> | undefined

  const mockMode = isMockMode()
  // Session ist beim Mounten schon 22 Minuten alt → SoC liegt nach realistischer
  // Ladekurve um die 57% (Peak + Anfang Decline sichtbar).
  const mockSessionStart = new Date(Date.now() - 22 * 60 * 1000)

  function appendPowerPoint(kw: number | null, lastUpdatedAt: string | null) {
    if (kw == null) return
    const ts = lastUpdatedAt ? new Date(lastUpdatedAt).getTime() : Date.now()
    // Dedup: skip if timestamp already present (e.g. realtime tick == last history point)
    const last = powerHistory.value[powerHistory.value.length - 1]
    if (last && last.ts === ts) return
    powerHistory.value = [...powerHistory.value, { ts, kw }].slice(-HISTORY_MAX_POINTS)
  }

  const fetchHistory = async () => {
    if (!carId.value) return
    try {
      const res = await axios.get<{ points: { timestamp: string, powerKw: number }[] }>(
        `/api/cars/${carId.value}/live/charging/history?minutes=30`
      )
      powerHistory.value = res.data.points.map(p => ({
        ts: new Date(p.timestamp).getTime(),
        kw: p.powerKw,
      })).slice(-HISTORY_MAX_POINTS)
    } catch {
      // History-Fetch ist best-effort - bei Fehler bleibt der Buffer leer
      // und wird durch die realtime-Ticks gefuellt.
    }
  }

  const fetch = async () => {
    if (!carId.value) return

    if (mockMode) {
      const d = buildMockData(mockSessionStart)
      // Beim ersten Tick die Historie seeden, sonst nur Tick anhaengen.
      if (powerHistory.value.length === 0) {
        powerHistory.value = seedMockHistory(mockSessionStart)
      }
      data.value = d
      appendPowerPoint(d.powerKw, d.lastUpdatedAt)
      return
    }

    try {
      const res = await axios.get<LiveChargingData>(`/api/cars/${carId.value}/live/charging`)
      data.value = res.data
      if (res.data.isActive) appendPowerPoint(res.data.powerKw, res.data.lastUpdatedAt)
      else powerHistory.value = []   // Session beendet → History zuruecksetzen
      error.value = null
    } catch {
      error.value = 'error'
    }
  }

  watchEffect(() => {
    clearInterval(intervalId)
    powerHistory.value = []   // Wechsel des Autos → History zuruecksetzen
    if (carId.value) {
      loading.value = true
      if (mockMode) {
        // Mock-Mode: keine History-API, seedMockHistory laeuft im ersten fetch()-Tick.
        fetch().finally(() => { loading.value = false })
      } else {
        // Real-Mode: erst Backend-History laden (Curve wird sofort sichtbar), dann live pollen.
        fetchHistory()
          .then(() => fetch())
          .finally(() => { loading.value = false })
      }
      intervalId = setInterval(fetch, 5000)
    }
  })

  onUnmounted(() => clearInterval(intervalId))

  return { data, powerHistory, loading, error, refresh: fetch }
}

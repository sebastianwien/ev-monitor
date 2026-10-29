import { ref, computed, type Ref } from 'vue'
import api from '../api/axios'
import { useAuthStore } from '../stores/auth'
import { purchasesAvailable } from '../utils/iapPolicy'
import { mergeSocSeries, type CurvePoint, type SocPoint } from '../components/charging/powerCurveSeries'

/**
 * Ladekurve einer Ladung im Overlay (PowerCurveModal), geteilt von Log-Feed und Logbuch.
 * `stats` liefert den Fahrzeug-Schnitt als Referenzlinie, `formatDate` die Unterzeile.
 */
export function usePowerCurve(
  stats: Ref<{ avgConsumptionKwhPer100km?: number | string | null } | null>,
  formatDate: (iso: string) => string,
) {
  const authStore = useAuthStore()
  // Power-Curve: lazy-loaded pro Log-ID, einmal geladen wird's gecached.
  // LRU mit Hard-Cap auf POWER_CURVE_CACHE_MAX: bei einem Power-User der hunderte
  // Logs aufklappt waere die Map sonst unbounded (~600 Byte pro Eintrag).
  const POWER_CURVE_CACHE_MAX = 50
  // Die Kurve wird im Overlay gezeigt, nicht inline: in der Feed-Zeile blieb ihr
  // nur eine gestauchte Resthoehe. Es ist immer hoechstens eine offen.
  const powerCurveEntry = ref<any | null>(null)
  /** Was zu einer Ladung aufgezeichnet wurde: Leistungskurve (Tesla) oder
   *  Ladeverlauf (Smartcar). Nie beides - siehe PowerCurveResponse im Backend. */
  interface CurveData { points: CurvePoint[]; socPoints: SocPoint[] }
  const powerCurveCache = ref(new Map<string, CurveData>())
  const powerCurveLoading = ref(new Set<string>())

  function cachePut(logId: string, points: CurveData) {
    // LRU-Verhalten via JS-Map-Insertion-Order: bei Hit erst delete dann set
    // schiebt den Eintrag ans Ende; oldest fliegt raus wenn Cap erreicht.
    if (powerCurveCache.value.has(logId)) powerCurveCache.value.delete(logId)
    powerCurveCache.value.set(logId, points)
    while (powerCurveCache.value.size > POWER_CURVE_CACHE_MAX) {
      const oldest = powerCurveCache.value.keys().next().value
      if (oldest === undefined) break
      powerCurveCache.value.delete(oldest)
    }
    powerCurveCache.value = new Map(powerCurveCache.value)
  }

  async function openPowerCurve(entry: any) {
    const logId = entry.id
    powerCurveEntry.value = entry
    // Ohne Freischaltung zeigt das Overlay den Teaser. Der Abruf wuerde am
    // Server-Gate scheitern - gar nicht erst fragen. Der Ladeverlauf hat ein
    // eigenes, weiteres Gate.
    if (!authStore.canViewLiveAnalytics && !(entry?.hasSocCurve && authStore.canViewSocCurve)) return
    if (powerCurveCache.value.has(logId)) {
      // Recency-Touch fuer LRU: re-insert um den Eintrag ans Map-Ende zu schieben
      const cached = powerCurveCache.value.get(logId)!
      cachePut(logId, cached)
      return
    }
    powerCurveLoading.value.add(logId)
    powerCurveLoading.value = new Set(powerCurveLoading.value)
    try {
      // Teilladungen mitladen: der Connector trennt einen Ladelauf am
      // Zaehler-Reset, der Feed fuehrt die Logs ueber den identischen
      // Kilometerstand aber wieder zusammen. Der Ladeverlauf folgt derselben
      // Gruppierung und wird dadurch wieder durchgehend. Die Leistungskurve
      // bleibt beim Elternlog - Tesla kennt diesen Split nicht.
      const fetchCurve = (id: string) =>
        api.get<{ points: CurvePoint[]; socPoints: SocPoint[] }>(`/logs/${id}/power-curve`)
          .then(r => r.data)
          .catch(() => ({ points: [] as CurvePoint[], socPoints: [] as SocPoint[] }))

      const parent = await fetchCurve(logId)
      // Teilladungen nur nachladen, wenn es um einen Ladeverlauf geht: der
      // Connector trennt einen Lauf am Zaehler-Reset, der Feed fuehrt die Logs
      // ueber den identischen Kilometerstand aber wieder zusammen. Bei einer
      // Leistungskurve gibt es diesen Split nicht - dort waeren die zusaetzlichen
      // Requests reine Last.
      const topUpIds = parent.points?.length
        ? []
        : ((entry?._topUps ?? []) as any[]).map(t => t?.id).filter(Boolean)
      const topUps = topUpIds.length ? await Promise.all(topUpIds.map(fetchCurve)) : []

      cachePut(logId, {
        points: parent.points ?? [],
        socPoints: mergeSocSeries([parent, ...topUps].map(r => r?.socPoints)),
      })
    } catch {
      cachePut(logId, { points: [], socPoints: [] })
    } finally {
      powerCurveLoading.value.delete(logId)
      powerCurveLoading.value = new Set(powerCurveLoading.value)
    }
  }

  // Referenzlinie der Ladekurve: Verbrauch des Logs, sonst der Fahrzeug-Schnitt.
  function powerCurveConsumption(entry: any): number | null {
    if (entry?.consumptionKwhPer100km != null) return Number(entry.consumptionKwhPer100km)
    return stats.value?.avgConsumptionKwhPer100km != null ? Number(stats.value.avgConsumptionKwhPer100km) : null
  }

  // formatLogDate enthaelt bereits die Uhrzeit - kein zweites Mal anhaengen.
  const powerCurveSubtitle = computed(() => {
    const at = powerCurveEntry.value?.loggedAt
    return at ? formatDate(at) : ''
  })

  /** Kurve ansehen ist freigeschaltet: Leistungskurve mit Live-Analyse, Ladeverlauf mit eigenem Gate. */
  function canOpen(entry: any): boolean {
    return !!((entry?.hasPowerCurve && authStore.canViewLiveAnalytics) || (entry?.hasSocCurve && authStore.canViewSocCurve))
  }
  /** Knopf zeigen: offen, oder aufgezeichnet und kaufbar (sonst öffnet er den Teaser). */
  function available(entry: any): boolean {
    return canOpen(entry) || (!!(entry?.hasPowerCurve || entry?.hasSocCurve) && purchasesAvailable())
  }

  return {
    canOpen,
    available,
    entry: powerCurveEntry,
    cache: powerCurveCache,
    loading: powerCurveLoading,
    open: openPowerCurve,
    consumption: powerCurveConsumption,
    subtitle: powerCurveSubtitle,
  }
}

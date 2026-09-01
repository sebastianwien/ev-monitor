import { provide, inject, ref, computed, watch, onMounted, type InjectionKey, type Ref, type ComputedRef } from 'vue'
import { useDashboardStats } from './useDashboardStats'
import { useLogList } from './useLogList'

/**
 * Geteilter Zustand fuer Dashboard und Log-Feed. Beide Views teilen sich auf
 * Mobile einen persistenten Header (Auto-Card) und unterscheiden sich nur im
 * unteren Bereich - der State (Auto-Auswahl, Fahrzeuge, Provider-Polling,
 * Statistiken, Logs) darf daher nur EINMAL existieren.
 *
 * Frueher rief jede View useDashboardStats()/useLogList() eigenstaendig auf
 * (doppeltes Polling, nicht-geteilte Auswahl). Jetzt instanziiert das
 * CarContextLayout beides einmal via provideCarContext() und stellt es den
 * Body-Komponenten via useCarContext() bereit.
 */
export type CarContext =
  ReturnType<typeof useDashboardStats>
  & ReturnType<typeof useLogList>
  & {
    logsSection: Ref<HTMLElement | null>
    /** Template-Ref-Setter fuer das Scroll-Ziel (Template entpackt Refs -> Setter noetig). */
    setLogsSection: (el: unknown) => void
    /** Hoechster Kilometerstand aus den geladenen Logs (fuer Card-Details). */
    currentOdometerKm: ComputedRef<number | null>
  }

const CAR_CONTEXT: InjectionKey<CarContext> = Symbol('car-context')

/** Im Layout aufrufen: erzeugt die geteilten Instanzen + zentrale Orchestrierung. */
export function provideCarContext(): CarContext {
  const dash = useDashboardStats()
  const logsSection = ref<HTMLElement | null>(null)
  const logs = useLogList(dash.selectedCarId, dash.cars, logsSection)

  // Zentraler Reload bei Auto-Wechsel - laeuft einmal, unabhaengig vom aktiven Tab.
  watch(dash.selectedCarId, async (newId) => {
    if (newId) {
      await dash.fetchCarAndWltp(newId)
      await Promise.all([dash.fetchStatistics(), logs.fetchLogs(0), dash.fetchImplausibleCount(), dash.fetchPricelessCount()])
    } else {
      dash.stats.value = null
      dash.carInfo.value = null
      dash.wltp.value = null
      dash.implausibleCount.value = 0
    }
  })

  onMounted(() => dash.initCars())

  const currentOdometerKm = computed<number | null>(() => {
    let max: number | null = null
    for (const l of logs.logs.value) {
      const o = l.odometerKm
      if (typeof o === 'number' && (max == null || o > max)) max = o
    }
    return max
  })

  const setLogsSection = (el: unknown) => { logsSection.value = (el as HTMLElement | null) }

  const ctx: CarContext = { ...dash, ...logs, logsSection, setLogsSection, currentOdometerKm }
  provide(CAR_CONTEXT, ctx)
  return ctx
}

/** In Dashboard-/Logs-Body aufrufen: liefert den geteilten Zustand. */
export function useCarContext(): CarContext {
  const ctx = inject(CAR_CONTEXT)
  if (!ctx) throw new Error('useCarContext() muss innerhalb von CarContextLayout verwendet werden')
  return ctx
}

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, PlusIcon } from '@heroicons/vue/24/outline'
import CarSwitch from '../components/car/CarSwitch.vue'
import FeedHintCard from '../components/dashboard/FeedHintCard.vue'
import DeletedLogsEntry from '../components/dashboard/DeletedLogsEntry.vue'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import PricelessLogsModal from '../components/dashboard/PricelessLogsModal.vue'
import LogbookMonth from '../components/logbook/LogbookMonth.vue'
import LogbookDetail, { type LogbookSelection } from '../components/logbook/LogbookDetail.vue'
import { useCarContext } from '../composables/useCarContext'
import { useDataFeedback } from '../composables/useDataFeedback'
import { useIsMobile } from '../composables/useIsMobile'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { usePricelessDeepLink } from '../composables/usePricelessDeepLink'
import { useAutoOlder } from '../composables/useAutoOlder'
import { formatOlderWindow } from '../composables/useFeedWindow'
import type { DayEvent } from '../composables/storyDay'
import { useAuthStore } from '../stores/auth'
import { monthsOf } from '../utils/logbook'
import { phantomTeaserId, showsDrain, phantomEurFor } from '../utils/phantomDrain'
import { tripsCredit } from '../utils/tripMap'
import { buildTripCostPerKwhMap, tripGroupCostPer100km, tripFacts } from '../utils/tripCalculations'
import { purchasesAvailable } from '../utils/iapPolicy'

/**
 * /logs im neuen Design: das Logbuch. Der Monat ist ein Kapitel mit klebendem Kopf, darin ein Blatt je Tag
 * aus dem Tagesblatt der Story. Beim Scrollen lädt der Vormonat nach, der Knopf darunter ist der Rückfall.
 * Ein Tipp auf einen Eintrag öffnet ihn: unter 1024 px als Sheet, ab 1024 px in der rechten Spalte.
 */
const { t, locale } = useI18n()
const authStore = useAuthStore()
const upsellTarget = useAnalyticsUpsellTarget()
const wide = useIsMobile('(min-width: 1024px)')

const {
  selectedCarId, selectedCar, cars, carImageUrls, stats, setLogsSection,
  logs, trips, logsLoading, mergedLogFeed, hasAnyLogs,
  feedNextOlder, loadOlderFeed, refreshLogsAndGroups, mergeTripEntry,
  fetchImplausibleCount, fetchStatistics, fetchPricelessCount,
} = useCarContext()

// ── Bücher: Monate, Tage, Standverlust, Ladezyklen ───────────────────────────
// Kosten je Fahrt wie in der alten Liste: Preis der Ladung davor, sonst der eigene Schnitt
const cpkMap = computed(() => buildTripCostPerKwhMap(trips.value, logs.value, stats.value?.avgCostPerKwh ?? null))
const per100 = (list: any[]) => tripGroupCostPer100km(list, cpkMap.value, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)

/** Standverlust je Eintrag, nur wo er sichtbar ist: Supporter-Pack oder der eine Vorgeschmack. Wert wie in der alten Liste. */
const drains = computed(() => {
  const canView = authStore.canViewLiveAnalytics
  const teaser = phantomTeaserId(mergedLogFeed.value, canView)
  const avgCpk = stats.value?.avgCostPerKwh ?? null
  return new Map<string, { kwh: number; eur: number | null }>(mergedLogFeed.value
    .filter((e: any) => showsDrain(e, canView, teaser))
    .map((e: any) => [e.id, { kwh: e._phantomDrain.kwh, eur: phantomEurFor(e._phantomDrain, avgCpk) }]))
})
const unlockTo = computed(() => !authStore.canViewLiveAnalytics && purchasesAvailable() ? upsellTarget.value : null)
/** Je Fahrt Kosten und Verbrauch wie in der alten Liste (utils/tripCalculations). */
const facts = computed(() => {
  const cap = selectedCar.value?.effectiveBatteryCapacityKwh ?? null
  return Object.fromEntries((trips.value as any[]).map(trip => [trip.id, tripFacts(trip, cpkMap.value.get(trip.id) ?? null, cap)]))
})
const months = computed(() => monthsOf(trips.value, logs.value, { drains: drains.value, costPer100km: per100 }))
/** Die Minikarten tragen keinen eigenen Hinweis; er steht einmal am Ende (OSM: einer genügt für mehrere Karten). */
const credit = computed(() => tripsCredit(trips.value))

// ── Auswahl und Bearbeiten ──────────────────────────────────────────────────
const selection = ref<LogbookSelection | null>(null)
const selectedId = computed(() => selection.value?.kind === 'charge' ? selection.value.log.id : selection.value?.trip.id ?? null)
function select(e: DayEvent) {
  selection.value = e.kind === 'charge' ? { kind: 'charge', log: e.log as any } : { kind: 'trip', trip: e.trip }
}
watch(selectedCarId, () => { selection.value = null })
function changed() {
  refreshLogsAndGroups()
  fetchPricelessCount()
}
const merging = ref(false)
async function merge(earlier: DayEvent, later: DayEvent) {
  merging.value = true
  // Die spätere Fahrt gibt es danach nicht mehr, die frühere hat neue Werte
  if (selectedId.value === earlier.id || selectedId.value === later.id) selection.value = null
  try {
    await mergeTripEntry(earlier.id, later.id)
    refreshLogsAndGroups()
  } finally {
    merging.value = false
  }
}

// ── Vormonat nachladen: beim Scrollen, solange er etwas bringt; sonst bleibt der Knopf ──
const { older, setVisible, reset: resetOlder } = useAutoOlder({
  loading: logsLoading,
  count: () => logs.value.length + trips.value.length,
  enabled: () => hasAnyLogs.value,
  load: loadOlderFeed,
})
watch(selectedCarId, resetOlder)
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
onMounted(() => {
  if (typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting), { rootMargin: '0px 0px 600px 0px' })
  if (sentinel.value) observer.observe(sentinel.value)
})
watch(sentinel, (el, prev) => {
  if (prev) observer?.unobserve(prev)
  if (el) observer?.observe(el)
})
onUnmounted(() => observer?.disconnect())
const olderLabel = computed(() => formatOlderWindow(feedNextOlder.value, 'cycle', locale.value))

// ── Hinweise und ihre Listen ────────────────────────────────────────────────
const showImplausibleModal = ref(false)
const showPricelessModal = ref(false)
const implausibleModalDirty = ref(false)
usePricelessDeepLink(cars, selectedCarId, () => { showPricelessModal.value = true })

const dataFeedback = useDataFeedback()
onMounted(() => { dataFeedback.load() })
</script>

<template>
  <div class="md:mx-auto md:max-w-6xl md:px-6 md:pb-6">
    <div class="lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)] lg:items-start lg:gap-6">
      <div class="min-w-0 bg-white pb-6 dark:bg-gray-900 md:rounded-3xl md:edge-box" data-testid="logbook">
        <h1 class="sr-only">{{ t('logs.title') }}</h1>
        <!-- Das Auto nur nennen, wenn es mehrere gibt -->
        <header v-if="cars.length > 1" class="px-4 pb-2 pt-3 md:pt-5">
          <CarSwitch v-model="selectedCarId" :cars="cars" :car-image-urls="carImageUrls" />
        </header>
        <FeedHintCard class="mx-3 my-3 md:mx-4" @open-implausible="showImplausibleModal = true" @open-priceless="showPricelessModal = true" />

        <div :ref="setLogsSection" class="scroll-mt-4" :aria-busy="logsLoading">
          <div v-if="logsLoading && !hasAnyLogs" class="grid animate-pulse gap-3 px-4 py-4" aria-hidden="true">
            <div class="h-12 rounded-xl bg-gray-200 dark:bg-gray-700" />
            <div v-for="n in 3" :key="n" class="h-24 rounded-xl bg-gray-100 dark:bg-gray-800" />
          </div>
          <!-- Leer: sagen, was hier steht, und die Handlung anbieten -->
          <div v-else-if="!hasAnyLogs" class="mx-3 grid justify-items-center gap-3 rounded-2xl bg-gray-50 px-6 py-10 text-center dark:bg-white/5" data-testid="logbook-empty">
            <p class="max-w-xs text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.empty_title') }}</p>
            <router-link to="/erfassen" class="btn-3d inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-edge bg-green-600 px-5 text-[15px] font-semibold text-white hover:bg-green-700">
              <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('logs.empty_cta') }}
            </router-link>
          </div>
          <p v-else-if="!logsLoading && !months.length" class="mx-3 rounded-2xl bg-gray-50 px-6 py-8 text-center text-[14px] text-gray-600 dark:bg-white/5 dark:text-gray-300">
            {{ t('logs.empty_window') }}
          </p>

          <LogbookMonth v-for="m in months" :key="m.key" :month="m" :unlock-to="unlockTo" :facts="facts"
            :selected-id="selectedId" :merging="merging" :wide="wide" @select="select" @merge="merge" />

          <p v-if="credit" data-testid="logbook-map-credit" class="px-4 pt-3 text-right text-[10px] text-gray-400">{{ credit }}</p>
          <div v-if="hasAnyLogs" ref="sentinel" class="flex justify-center px-4 pt-5">
            <button type="button" data-testid="logbook-older" :disabled="logsLoading" @click="older"
              class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-edge bg-white px-4 text-[14px] font-semibold text-gray-800 hover:bg-gray-50 disabled:opacity-50 dark:bg-white/10 dark:text-gray-200">
              <ChevronDownIcon class="h-4 w-4" aria-hidden="true" />{{ t('logs.load_older', { range: olderLabel }) }}
            </button>
          </div>
        </div>

        <DeletedLogsEntry class="mx-1 mt-4" />
      </div>

      <LogbookDetail :selection="selection" :car-id="selectedCarId ?? ''" :wide="wide" @close="selection = null" @changed="changed" />
    </div>

    <ImplausibleLogsModal
      :car-id="selectedCarId"
      :open="showImplausibleModal"
      @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
      @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
    />
    <PricelessLogsModal
      :car-id="selectedCarId"
      :open="showPricelessModal"
      @close="showPricelessModal = false"
      @updated="() => { fetchPricelessCount(); refreshLogsAndGroups() }"
    />
  </div>
</template>

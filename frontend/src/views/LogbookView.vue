<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, PlusIcon, ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
import CarSwitch from '../components/car/CarSwitch.vue'
import FeedHintCard from '../components/dashboard/FeedHintCard.vue'
import DeletedLogsEntry from '../components/dashboard/DeletedLogsEntry.vue'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import PricelessLogsModal from '../components/dashboard/PricelessLogsModal.vue'
import LogbookMonth from '../components/logbook/LogbookMonth.vue'
import ChargeBook from '../components/logbook/ChargeBook.vue'
import LogbookDetail, { type LogbookSelection } from '../components/logbook/LogbookDetail.vue'
import ReassignCarSheet from '../components/dashboard/ReassignCarSheet.vue'
import { useCarContext } from '../composables/useCarContext'
import { useDataFeedback } from '../composables/useDataFeedback'
import { useIsMobile } from '../composables/useIsMobile'
import { useAnalyticsUpsellLink } from '../composables/useUpsellTarget'
import { usePricelessDeepLink } from '../composables/usePricelessDeepLink'
import { useAutoOlder } from '../composables/useAutoOlder'
import { useChargingProviderNames } from '../composables/useChargingProviderNames'
import { formatOlderWindow } from '../composables/useFeedWindow'
import type { DayEvent, DayLog } from '../composables/storyDay'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import { monthsOf, chargeMonthsOf, type ChargeLog } from '../utils/logbook'
import { phantomTeaserId, showsDrain, phantomEurFor } from '../utils/phantomDrain'
import { tripsCredit } from '../utils/tripMap'
import { buildTripCostPerKwhMap, tripGroupCostPer100km, tripFacts } from '../utils/tripCalculations'

/**
 * /logs im neuen Design: das Logbuch. Der Monat ist ein Kapitel mit klebendem Kopf, darin ein Blatt je Tag
 * aus dem Tagesblatt der Story. Beim Scrollen lädt der Vormonat nach, der Knopf darunter ist der Rückfall.
 * Ein Tipp auf einen Eintrag öffnet ihn: unter 1024 px als Sheet, ab 1024 px in der rechten Spalte.
 */
const { t, locale } = useI18n()
const authStore = useAuthStore()
const drainLink = useAnalyticsUpsellLink('logbook_drain')
const wide = useIsMobile('(min-width: 1024px)')

const {
  selectedCarId, selectedCar, cars, carImageUrls, stats, setLogsSection,
  logs, trips, logsLoading, mergedLogFeed, hasAnyLogs,
  feedNextOlder, loadOlderFeed, refreshLogsAndGroups, mergeTripEntry, latestTrip, priceAmendingLog,
  fetchImplausibleCount, fetchStatistics, fetchPricelessCount,
  otherCars, openReassignModal, reassignSuccessMessage,
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
const unlockTo = computed(() => authStore.canViewLiveAnalytics ? null : drainLink.value)
/** Je Fahrt Kosten und Verbrauch wie in der alten Liste (utils/tripCalculations). */
const facts = computed(() => {
  const cap = selectedCar.value?.effectiveBatteryCapacityKwh ?? null
  return Object.fromEntries((trips.value as any[]).map(trip => [trip.id, tripFacts(trip, cpkMap.value.get(trip.id) ?? null, cap)]))
})
const cardNames = useChargingProviderNames()
// Seitenart je Auto: hat es mindestens eine Fahrt, ein Blatt je Tag; sonst eine Zeile je Ladung mit der Strecke dazwischen
const hasTrips = computed(() => latestTrip.value != null)
const chargeMonths = computed(() => hasTrips.value ? [] : chargeMonthsOf(logs.value as ChargeLog[]))
const bookEmpty = computed(() => hasTrips.value ? !months.value.length : !chargeMonths.value.length)
const months = computed(() => monthsOf(trips.value, logs.value, { drains: drains.value, costPer100km: per100 }))
/** Die Minikarten tragen keinen eigenen Hinweis; er steht einmal am Ende (OSM: einer genügt für mehrere Karten). */
const credit = computed(() => tripsCredit(trips.value))

// ── Auswahl und Bearbeiten ──────────────────────────────────────────────────
const selection = ref<LogbookSelection | null>(null)
/** Bündel je id aus dem Buch: das Detail zeigt nach Nachtragen oder Löschen die frischen Teile */
const bundles = computed(() => {
  const byId = new Map<string, DayLog[]>()
  for (const m of months.value) for (const item of m.items) {
    if (item.kind === 'day') for (const r of item.rows) if (r.kind === 'charge' && r.parts) byId.set(r.id, r.parts)
  }
  for (const m of chargeMonths.value) for (const e of m.entries) if (e.kind === 'charge' && e.parts) byId.set(e.key, e.parts)
  return byId
})
const shown = computed<LogbookSelection | null>(() => {
  const s = selection.value
  if (s?.kind !== 'bundle') return s
  const parts = bundles.value.get(s.id)
  return parts ? { ...s, parts } : null
})
// Gibt es das Bündel nach dem Laden nicht mehr (umgebucht, Teil gelöscht), ist nichts mehr gewählt
watch([shown, logsLoading], ([s, busy]) => { if (!s && !busy && selection.value) selection.value = null })
const selectedId = computed(() => {
  const s = selection.value
  return s?.kind === 'charge' ? s.bundleId ?? s.log.id : s?.kind === 'bundle' ? s.id : s?.trip.id ?? null
})
function select(e: DayEvent) {
  if (e.kind === 'charge' && e.parts) return selectBundle(e.id, e.parts)
  selection.value = e.kind === 'charge' ? { kind: 'charge', log: e.log as any } : { kind: 'trip', trip: e.trip }
  analytics.track('logbook_detail', { kind: e.kind === 'charge' ? 'ladung' : 'fahrt' })
}
const selectCharge = (log: ChargeLog) => {
  selection.value = { kind: 'charge', log: log as any }
  analytics.track('logbook_detail', { kind: 'ladung' })
}
function selectBundle(id: string, parts: DayLog[]) {
  selection.value = { kind: 'bundle', id, parts }
  analytics.track('logbook_detail', { kind: 'buendel' })
}
function backToBundle() {
  const s = selection.value
  if (s?.kind === 'charge' && s.bundleId) selection.value = { kind: 'bundle', id: s.bundleId, parts: bundles.value.get(s.bundleId) ?? [] }
}
/** Umbuchen: beim Bündel alle Teile, wie die Ladegruppe der Liste */
function reassign() {
  const s = shown.value
  if (s?.kind === 'bundle') openReassignModal({ _isLadegruppe: true, _topUps: [...s.parts].reverse() })
  else if (s?.kind === 'charge') openReassignModal(s.log)
}
/** km-Stand nachtragen: die Ladung im Bearbeiten öffnen */
function fixOdometer(logId: string) {
  const log = (logs.value as ChargeLog[]).find(l => l.id === logId)
  if (log) selectCharge(log)
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
  load: () => { analytics.track('logbook_older'); loadOlderFeed() },
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
// Einmal je Besuch, nach dem ersten Laden: welche Buchart der Nutzer sieht
let opened = false
watch(logsLoading, (busy) => {
  if (busy || opened || !selectedCarId.value) return
  opened = true
  analytics.track('logbook_open', { book: hasTrips.value ? 'tage' : 'ladungen' })
})
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
        <p v-if="reassignSuccessMessage" role="status" class="mx-3 mb-3 flex items-center gap-2 rounded-xl bg-green-50 px-3 py-2.5 text-[14px] font-semibold text-green-800 md:mx-4 dark:bg-green-400/10 dark:text-green-300">
          <ArrowsRightLeftIcon class="h-4 w-4 shrink-0" aria-hidden="true" />{{ reassignSuccessMessage }}
        </p>

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
          <p v-else-if="!logsLoading && bookEmpty" class="mx-3 rounded-2xl bg-gray-50 px-6 py-8 text-center text-[14px] text-gray-600 dark:bg-white/5 dark:text-gray-300">
            {{ t('logs.empty_window') }}
          </p>

          <ChargeBook v-if="!hasTrips" :months="chargeMonths" :selected-id="selectedId"
            @select="selectCharge" @select-bundle="selectBundle" @amend="log => { priceAmendingLog = log }" @fix="fixOdometer" />
          <template v-else>
            <LogbookMonth v-for="m in months" :key="m.key" :month="m" :unlock-to="unlockTo" :facts="facts" :card-names="cardNames"
              :selected-id="selectedId" :merging="merging" :wide="wide" @select="select" @merge="merge" />
          </template>

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

      <LogbookDetail :selection="shown" :car-id="selectedCarId ?? ''" :wide="wide" :can-reassign="otherCars.length > 0"
        :trip-facts="shown?.kind === 'trip' ? facts[shown.trip.id] ?? null : null" @close="selection = null" @changed="changed"
        @back="backToBundle" @open="s => { selection = s }" @amend="log => { priceAmendingLog = log }" @reassign="reassign" />
    </div>

    <ImplausibleLogsModal
      :car-id="selectedCarId"
      :open="showImplausibleModal"
      @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
      @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
    />
    <ReassignCarSheet @saved="selection = null" />
    <PricelessLogsModal
      :car-id="selectedCarId"
      :open="showPricelessModal"
      @close="showPricelessModal = false"
      @updated="() => { fetchPricelessCount(); refreshLogsAndGroups() }"
    />
  </div>
</template>

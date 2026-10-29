<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ExclamationTriangleIcon, CurrencyEuroIcon, BoltIcon, ArrowPathIcon, HeartIcon, InformationCircleIcon, MapIcon,
} from '@heroicons/vue/24/outline'
import FeedHintRow from './FeedHintRow.vue'
import WattBadge from '../shared/WattBadge.vue'
import { useCarContext } from '../../composables/useCarContext'
import { useCoinStore } from '../../stores/coins'
import { useCountryStore } from '../../stores/country'
import { getPricing } from '../../config/pricingConfig'
import { purchasesAvailable } from '../../utils/iapPolicy'
import { wattPossiblePerLogMax } from '../../utils/wattPreview'
import { feedHints, tripsHintVisible } from '../../utils/feedHints'
import { hasFreeDataSource, isAutoSyncUnsupported, brandDeliversTrips, tripsComeByImport, normalizeBrand } from '../../composables/useCarAutoSyncProvider'
import polestarService from '../../api/polestarService'
import { subscriptionService, type SubscriptionTier } from '../../api/subscriptionService'

/**
 * Hinweise über dem Log-Feed bzw. dem Logbuch, eine Karte statt eines Bannerstapels: erst Aufgaben,
 * dann höchstens ein Angebot, zuletzt der Tipp (utils/feedHints). Die Listen zu den Aufgaben
 * (unplausibel, ohne Preis) öffnet die Seite.
 */
const emit = defineEmits<{ 'open-implausible': []; 'open-priceless': [] }>()
const { t } = useI18n()
const {
  selectedCar, latestTrip, logsLoading, teslaStatus, implausibleCount, implausibleBannerDismissed, dismissImplausibleBanner,
  pricelessCount, pricelessBannerDismissed, dismissPricelessBanner,
} = useCarContext()
const coinStore = useCoinStore()
coinStore.ensureCatalog()
const wattForAllPriceless = computed(() => wattPossiblePerLogMax(coinStore.catalog) * pricelessCount.value)

const subscriptionTier = ref<SubscriptionTier | null>(null)

// Ob das ausgewaehlte Auto bereits kostenlos Daten liefert (Tesla/XPeng) - dreht die
// AutoSync- vs. Supporter-Weiche fuer alle drei Banner unten. Kein Auto ausgewaehlt
// zaehlt als "keine freie Quelle", wie zuvor bei den einzelnen Brand-Checks.
const selectedCarHasFreeSource = computed(() =>
  selectedCar.value != null && hasFreeDataSource(selectedCar.value)
)

// Marke ohne AutoSync-Weg (Zeekr, Leapmotor, Lucid, Rivian): ein Abo wuerde ihre Daten nicht
// anfassen, also auch keine AutoSync-Banner. Kein Auto ausgewaehlt zaehlt als "unbekannt".
const selectedCarAutoSyncUnsupported = computed(() =>
  selectedCar.value != null && isAutoSyncUnsupported(selectedCar.value)
)

// AutoSync Live discoverability banner: temporarily disabled (flip flag to re-enable).
// Shown to users without a free data source and without Live, dismissible. Position:
// below ConsumptionInfoBox.
const LIVE_BANNER_ENABLED = false
const LS_LIVE_BANNER_DISMISSED = 'autosync_live_banner_dismissed'
const liveBannerDismissed = ref(localStorage.getItem(LS_LIVE_BANNER_DISMISSED) === 'true')
const showLiveBanner = computed(() =>
  LIVE_BANNER_ENABLED
  && purchasesAvailable()
  && subscriptionTier.value != null
  && subscriptionTier.value !== 'AUTOSYNC_LIVE'
  && !selectedCarHasFreeSource.value
  && !selectedCarAutoSyncUnsupported.value
  && !liveBannerDismissed.value
)
function dismissLiveBanner() {
  liveBannerDismissed.value = true
  localStorage.setItem(LS_LIVE_BANNER_DISMISSED, 'true')
}

// AutoSync discoverability banner: shown to users without a free data source and
// without any subscription (tier NONE), dismissible. Position: below ConsumptionInfoBox.
const countryStore = useCountryStore()
const autoSyncPrice = computed(() => getPricing(countryStore.country).monthly)
const LS_AUTOSYNC_BANNER_DISMISSED = 'autosync_banner_dismissed'
const autoSyncBannerDismissed = ref(localStorage.getItem(LS_AUTOSYNC_BANNER_DISMISSED) === 'true')
const showAutoSyncBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && !selectedCarHasFreeSource.value
  && !selectedCarAutoSyncUnsupported.value
  && !autoSyncBannerDismissed.value
)
function dismissAutoSyncBanner() {
  autoSyncBannerDismissed.value = true
  localStorage.setItem(LS_AUTOSYNC_BANNER_DISMISSED, 'true')
}

// Supporter-Banner: das Gegenstueck zum AutoSync-Banner darueber, gleicher Slot,
// gleiches Dismiss-Verhalten. Tesla-/XPeng-Fahrer bekommen die Datenerfassung gratis,
// AutoSync hat ihnen also nichts zu verkaufen - die Auswertungsebene schon. Ohne diesen
// Banner erfahren sie vom Pack nur, wenn sie zufaellig auf ein gesperrtes Widget klicken.
const supporterPrice = computed(() => getPricing(countryStore.country).supporterMonthly)
const LS_SUPPORTER_BANNER_DISMISSED = 'supporter_banner_dismissed'
const supporterBannerDismissed = ref(localStorage.getItem(LS_SUPPORTER_BANNER_DISMISSED) === 'true')
const showSupporterBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && selectedCarHasFreeSource.value
  && !supporterBannerDismissed.value
)
function dismissSupporterBanner() {
  supporterBannerDismissed.value = true
  localStorage.setItem(LS_SUPPORTER_BANNER_DISMISSED, 'true')
}

const LS_COST_TIP_DISMISSED = 'logfeed_cost_reuse_tip_dismissed'
const costTipDismissed = ref(localStorage.getItem(LS_COST_TIP_DISMISSED) === 'true')
const showCostTip = computed(() => !costTipDismissed.value)
function dismissCostTip() {
  costTipDismissed.value = true
  localStorage.setItem(LS_COST_TIP_DISMISSED, 'true')
}

// Eine Hinweis-Karte statt vier Bannern, Reihenfolge und Deckel in utils/feedHints.
// Fahrten automatisch erfassen: Marke mit Fahrten, das Auto hat noch keine, nicht weggeklickt (je Auto).
// Mit der ersten Fahrt verschwindet der Hinweis von selbst.
const LS_TRIPS_HINT_DISMISSED = 'trips_hint_dismissed'
function readDismissedTripsHint(): string[] {
  try { return JSON.parse(localStorage.getItem(LS_TRIPS_HINT_DISMISSED) ?? '[]') } catch { return [] }
}
const tripsHintDismissed = ref<string[]>(readDismissedTripsHint())
// Steht die Verbindung schon (Tesla, Polestar), kommen die Fahrten ohnehin: dann kein Hinweis
const polestarActive = ref<Set<string>>(new Set())
watch(() => selectedCar.value?.id, async () => {
  const car = selectedCar.value
  if (!car || normalizeBrand(car) !== 'POLESTAR') return
  try {
    const connections = await polestarService.getConnections()
    polestarActive.value = new Set(connections.filter(c => c.status === 'ACTIVE').map(c => c.carId))
  } catch { /* ohne Status gilt: nicht verbunden */ }
}, { immediate: true })
const connected = computed(() => {
  const car = selectedCar.value
  if (!car) return false
  const tesla = teslaStatus.value
  const teslaConnected = !!tesla?.connected && (tesla.carId === car.id || tesla.carId == null)
    && normalizeBrand(car) === 'TESLA'
  return teslaConnected || polestarActive.value.has(car.id)
})
const showTripsHint = computed(() => {
  const car = selectedCar.value
  return !!car && tripsHintVisible({
    brandDeliversTrips: brandDeliversTrips(car),
    connected: connected.value,
    loading: logsLoading.value,
    hasTrip: latestTrip.value != null,
    dismissed: tripsHintDismissed.value.includes(car.id),
  })
})
const tripsByImport = computed(() => !!selectedCar.value && tripsComeByImport(selectedCar.value))
function dismissTripsHint() {
  const id = selectedCar.value?.id
  if (!id) return
  tripsHintDismissed.value = [...tripsHintDismissed.value, id]
  try { localStorage.setItem(LS_TRIPS_HINT_DISMISSED, JSON.stringify(tripsHintDismissed.value)) } catch { /* privater Modus */ }
}

const visibleHints = computed(() => feedHints({
  implausible: implausibleCount.value > 0 && !implausibleBannerDismissed.value,
  priceless: pricelessCount.value > 0 && !pricelessBannerDismissed.value,
  trips: showTripsHint.value,
  live: showLiveBanner.value,
  autosync: showAutoSyncBanner.value,
  supporter: showSupporterBanner.value,
  costTip: showCostTip.value,
}))

onMounted(async () => {
  try {
    const status = await subscriptionService.getStatus()
    subscriptionTier.value = status.tier ?? 'NONE'
  } catch {
    subscriptionTier.value = null
  }
})
</script>

<template>
  <ul v-if="visibleHints.length" data-testid="feed-hints"
    class="divide-y divide-gray-200 rounded-2xl bg-white edge-card dark:divide-gray-700/70 dark:bg-gray-800">
    <template v-for="hint in visibleHints" :key="hint">
      <FeedHintRow v-if="hint === 'implausible'" data-testid="implausible-banner" task :icon="ExclamationTriangleIcon"
        :dismiss-label="t('dashboard.implausible_dismiss')" @open="emit('open-implausible')" @dismiss="dismissImplausibleBanner">
        {{ t('dashboard.implausible_banner', { n: implausibleCount, noun: implausibleCount === 1 ? t('dashboard.implausible_entry') : t('dashboard.implausible_entries') }) }}
        <template #sub>{{ t('logs.hints.check') }}</template>
      </FeedHintRow>
      <FeedHintRow v-else-if="hint === 'priceless'" data-testid="priceless-banner" task :icon="CurrencyEuroIcon"
        :dismiss-label="t('priceless.dismiss')" @open="emit('open-priceless')" @dismiss="dismissPricelessBanner">
        {{ t('priceless.banner', pricelessCount) }}
        <template #sub>{{ t('priceless.banner_cta') }} <WattBadge :amount="wattForAllPriceless" up-to /></template>
      </FeedHintRow>
      <!-- Die Importseite öffnet für Tesla, Polestar und XPeng den passenden Abschnitt des aktiven Autos -->
      <FeedHintRow v-else-if="hint === 'trips'" data-testid="trips-hint" :icon="MapIcon" to="/imports"
        :dismiss-label="t('logbook.trips_dismiss')" @dismiss="dismissTripsHint">
        <i18n-t keypath="logbook.trips_hint" tag="span"><template #title><b class="font-semibold">{{ tripsByImport ? t('logbook.trips_title_import') : t('logbook.trips_title') }}</b></template></i18n-t>
        <template #sub>{{ tripsByImport ? t('logbook.trips_import') : t('logbook.trips_connect') }}</template>
      </FeedHintRow>
      <FeedHintRow v-else-if="hint === 'live'" :icon="BoltIcon" to="/upgrade"
        :dismiss-label="t('dashboard.live_banner_dismiss')" @dismiss="dismissLiveBanner">
        <i18n-t keypath="logs.hints.live" tag="span"><template #product><b class="font-semibold">AutoSync Live</b></template></i18n-t>
        <template #sub>{{ t('logs.hints.more') }}</template>
      </FeedHintRow>
      <FeedHintRow v-else-if="hint === 'autosync'" data-testid="autosync-banner" :icon="ArrowPathIcon" to="/upgrade"
        :dismiss-label="t('dashboard.autosync_banner_dismiss')" @dismiss="dismissAutoSyncBanner">
        <i18n-t keypath="logs.hints.autosync" tag="span"><template #product><b class="font-semibold">AutoSync</b></template></i18n-t>
        <template #sub>{{ t('logs.hints.per_month', { price: autoSyncPrice }) }} &middot; {{ t('logs.hints.more') }}</template>
      </FeedHintRow>
      <FeedHintRow v-else-if="hint === 'supporter'" data-testid="supporter-banner" :icon="HeartIcon" to="/supporter"
        :dismiss-label="t('dashboard.supporter_banner_dismiss')" @dismiss="dismissSupporterBanner">
        <i18n-t keypath="logs.hints.supporter" tag="span"><template #pack><b class="font-semibold">{{ t('dashboard.supporter_banner_pack') }}</b></template></i18n-t>
        <template #sub>{{ t('logs.hints.per_month', { price: supporterPrice }) }} &middot; {{ t('logs.hints.more') }}</template>
      </FeedHintRow>
      <FeedHintRow v-else-if="hint === 'cost_tip'" plain :icon="InformationCircleIcon"
        :dismiss-label="t('dashboard.cost_reuse_tip_dismiss')" @dismiss="dismissCostTip">
        <span class="text-[13.5px] text-gray-600 dark:text-gray-300">
          <i18n-t keypath="dashboard.cost_reuse_tip_text" tag="span">
            <template #settings>
              <router-link to="/settings" class="font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.cost_reuse_tip_settings') }}</router-link>
            </template>
          </i18n-t>
        </span>
      </FeedHintRow>
    </template>
  </ul>
</template>

<template>
<div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
  <PublicNav />

  <!-- Hero -->
  <header v-if="!isRedditSource" class="mx-auto grid max-w-7xl gap-[18px] px-4 pb-5 pt-7 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] lg:items-center lg:gap-x-12 lg:px-6 lg:pb-8 lg:pt-12">
    <div class="grid gap-[18px]">
      <p class="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.08em] text-gray-500 dark:text-gray-400">
        <span class="h-2 w-2 rounded-full bg-green-600 ring-4 ring-green-100 dark:bg-green-400 dark:ring-green-950" aria-hidden="true"></span>
        {{ t('models_ranking.hero.eyebrow', { date: asOf }) }}
      </p>
      <h1 class="max-w-[18ch] text-balance text-[clamp(30px,7.4vw,52px)] font-bold leading-[1.04] tracking-tight text-gray-900 dark:text-gray-100">
        {{ t('models_ranking.hero.title_start') }}
        <span class="text-green-600 dark:text-green-400">{{ t('models_ranking.hero.title_highlight') }}</span>
        {{ t('models_ranking.hero.title_end') }}
      </h1>
      <p v-if="totalLogs > 0" class="max-w-[60ch] text-gray-600 dark:text-gray-400">
        {{ t('models_ranking.hero.lede', { logs: formatNumber(totalLogs), drivers: formatNumber(platformStats?.userCount ?? 0) }) }}
      </p>
    </div>
    <NeedsCheckCard v-model="needs" :summary="needsSummary">
      <PriorityPicker v-model="priority" />
    </NeedsCheckCard>
    <dl class="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-gray-200 bg-gray-200 sm:grid-cols-4 lg:col-span-2 dark:border-gray-800 dark:bg-gray-800">
      <div v-for="fact in heroFacts" :key="fact.label" class="flex flex-col-reverse bg-white px-3.5 py-3 dark:bg-gray-900">
        <dt class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ fact.label }}</dt>
        <dd class="text-[22px] font-semibold leading-tight tracking-tight tabular-nums" :class="fact.highlight ? 'text-orange-700 dark:text-orange-400' : 'text-gray-900 dark:text-gray-100'">{{ fact.value }}</dd>
      </div>
    </dl>
  </header>

  <!-- Reddit hero (utm_source=reddit), same as the classic list -->
  <header v-else class="mx-auto mb-6 max-w-7xl lg:px-6 lg:pt-6">
    <div class="bg-gradient-to-br from-gray-900 to-green-900 px-6 py-10 sm:px-10 sm:py-12">
      <div class="mb-4 inline-flex items-center gap-1.5 rounded-full bg-green-500/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-green-300">
        {{ t('models_list.reddit.badge') }}
      </div>
      <h1 class="mb-3 text-3xl font-bold leading-tight text-white sm:text-4xl">{{ t('models_list.reddit.title') }}</h1>
      <p class="mb-6 max-w-xl text-lg leading-relaxed text-gray-300">{{ t('models_list.reddit.subtitle') }}</p>
      <div class="mb-6 flex flex-wrap gap-3">
        <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3.5 py-1.5 text-sm font-medium text-white">
          <ChartBarIcon class="h-4 w-4 text-green-400" aria-hidden="true" />
          {{ models.length }} {{ t('models_list.hero.models_count') }}
        </span>
        <span v-if="platformStats" class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3.5 py-1.5 text-sm font-medium text-white">
          <ArrowTrendingUpIcon class="h-4 w-4 text-green-400" aria-hidden="true" />
          {{ formatNumber(platformStats.tripCount) }} {{ t('models_list.reddit.trips') }}
        </span>
      </div>
      <div class="flex flex-wrap gap-3">
        <a :href="registerPath" class="inline-flex items-center gap-2 rounded-sm bg-green-500 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-green-400">
          {{ t('models_list.reddit.cta') }}
          <ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
        </a>
        <span class="self-center text-xs text-gray-400">{{ t('models_list.reddit.no_subscription') }}</span>
      </div>
    </div>
  </header>

  <div class="mx-auto grid max-w-7xl grid-cols-1 gap-[18px] pb-10 lg:grid-cols-[300px_minmax(0,1fr)] lg:items-start lg:gap-7 lg:px-6">
    <!-- Filters -->
    <aside class="grid grid-cols-1 gap-3.5 px-4 lg:sticky lg:top-24 lg:px-0" :aria-label="t('models_ranking.filters.category')">
      <label class="relative block">
        <span class="sr-only">{{ t('models_ranking.filters.search_label') }}</span>
        <MagnifyingGlassIcon class="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500 dark:text-gray-400" aria-hidden="true" />
        <input
          v-model="query"
          type="search"
          autocomplete="off"
          :placeholder="t('models_ranking.filters.search_placeholder')"
          class="h-12 w-full rounded-xl border border-gray-200 bg-white pl-11 pr-3.5 text-base text-gray-900 placeholder:text-gray-500 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100 dark:placeholder:text-gray-400"
        />
      </label>

      <div>
        <p class="mb-2 text-[11.5px] font-semibold uppercase tracking-[0.08em] text-gray-500 dark:text-gray-400">{{ t('models_ranking.filters.category') }}</p>
        <div class="[scrollbar-width:none] [&::-webkit-scrollbar]:hidden -mx-4 flex gap-2 overflow-x-auto px-4 py-0.5 lg:mx-0 lg:flex-wrap lg:overflow-visible lg:px-0" role="group" :aria-label="t('models_ranking.filters.category')">
          <button
            v-for="c in categoryChips"
            :key="c.key ?? 'all'"
            type="button"
            :aria-pressed="category === c.key"
            :class="chipClass(category === c.key)"
            @click="category = c.key"
          >
            {{ c.label }}<span class="text-xs tabular-nums opacity-65">{{ c.count }}</span>
          </button>
          <button
            type="button"
            :aria-pressed="tripOnly"
            :class="chipClass(tripOnly)"
            data-testid="trip-chip"
            @click="tripOnly = !tripOnly"
          >
            <CheckIcon v-if="tripOnly" class="h-4 w-4" aria-hidden="true" />{{ t('models_ranking.filters.trip_chip') }}
          </button>
        </div>
      </div>

      <ul class="flex flex-wrap gap-x-4 gap-y-1.5 text-[13px] text-gray-600 dark:text-gray-400" :aria-label="t('models_ranking.legend.title')">
        <li class="inline-flex items-center gap-1.5"><i class="h-3 w-3 rounded-full bg-green-600 dark:bg-green-400"></i>{{ t('models_ranking.legend.real') }}</li>
        <li class="inline-flex items-center gap-1.5"><i class="h-2 w-[22px] rounded bg-green-200 dark:bg-green-900"></i>{{ t('models_ranking.legend.band') }}</li>
        <li class="inline-flex items-center gap-1.5"><i class="h-3 w-3 rounded-full border-2 border-gray-400 dark:border-gray-500"></i>{{ t('models_ranking.legend.wltp') }}</li>
        <li class="inline-flex items-center gap-1.5"><i class="h-[3px] w-[22px] rounded-sm bg-orange-500 dark:bg-orange-400"></i>{{ t('models_ranking.legend.gap') }}</li>
      </ul>
    </aside>

    <!-- Ranking -->
    <section class="mr-board min-w-0 lg:overflow-clip lg:rounded-2xl lg:border lg:border-gray-200 lg:bg-white lg:shadow-sm dark:lg:border-gray-800 dark:lg:bg-gray-900" :aria-label="t('models_ranking.sort.label')">
      <div class="sticky z-20 border-b border-gray-200 bg-gray-50 px-4 pt-2.5 lg:px-5 lg:pt-3 dark:border-gray-800 dark:bg-gray-950 lg:bg-white dark:lg:bg-gray-900" :class="stickyTopClass">
        <div class="[scrollbar-width:none] [&::-webkit-scrollbar]:hidden -mx-4 flex gap-2 overflow-x-auto px-4 pb-2 lg:mx-0 lg:px-0 lg:pb-2.5" role="group" :aria-label="t('models_ranking.sort.label')">
          <button
            v-for="s in RANKING_SORTS"
            :key="s"
            type="button"
            :aria-pressed="sort === s"
            :class="chipClass(sort === s, true)"
            @click="sort = s"
          >{{ t(`models_ranking.sort.${s}`) }}</button>
          <button
            ref="assumptionsChip"
            type="button"
            :class="chipClass(false, true)"
            class="ml-auto border border-dashed border-gray-400 !bg-transparent dark:border-gray-600"
            aria-haspopup="dialog"
            :aria-expanded="assumptionsOpen"
            data-testid="assumptions-chip"
            @click="openAssumptions"
          >{{ t('models_ranking.assumptions.chip') }}</button>
        </div>
        <div class="flex justify-between gap-3 whitespace-nowrap pb-2 pt-0.5 text-[13px] text-gray-500 dark:text-gray-400">
          <span aria-live="polite">{{ loading ? '' : t('models_ranking.count', { shown: ranked.length, total: models.length }) }}</span>
          <span class="truncate">
            <span class="hidden lg:inline">{{ t(`models_ranking.sort.note_${sort}`) }} · </span>{{ t('models_ranking.scale', { unit: consumptionUnitLabel() }) }}
          </span>
        </div>
        <div class="mr-grid mr-ruler h-[22px] lg:h-[38px]" aria-hidden="true">
          <span class="mr-who hidden self-center text-left text-[11.5px] font-semibold text-gray-700 lg:block dark:text-gray-300">{{ t('models_ranking.columns.model') }}</span>
          <div class="mr-ld relative h-[22px] text-[10.5px] tabular-nums text-gray-500 dark:text-gray-400">
            <span
              v-for="tick in axis.ticks"
              :key="tick"
              class="absolute top-1 -translate-x-1/2 after:absolute after:left-1/2 after:top-3.5 after:h-1.5 after:w-px after:bg-gray-300 dark:after:bg-gray-700"
              :style="{ left: `${tickPosition(tick)}%` }"
            >{{ formatDecimal(tick, tickDecimals) }}</span>
          </div>
          <span v-for="col in columnHeads" :key="col.cls" :class="[col.cls, col.cls === 'mr-c2' || col.cls === 'mr-c4' ? 'xl:block' : 'lg:block']" class="hidden self-center whitespace-nowrap text-right text-[11.5px] font-semibold leading-tight text-gray-700 dark:text-gray-300">
            {{ col.label }}<br><span class="font-normal text-gray-500 dark:text-gray-400">{{ col.unit }}</span>
          </span>
        </div>
      </div>

      <!-- Loading: skeleton rows -->
      <ol v-if="loading" class="list-none" aria-hidden="true">
        <li v-for="n in 8" :key="n" class="mr-grid border-b border-gray-200 px-4 py-3 lg:px-5 dark:border-gray-800">
          <span class="mr-rk h-3 w-4 justify-self-end rounded bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
          <span class="mr-th h-9 w-[52px] rounded-lg bg-gray-200 lg:h-[34px] lg:w-12 dark:bg-gray-800 animate-pulse"></span>
          <span class="mr-who h-4 w-3/4 rounded bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
          <span class="mr-ld h-2 rounded bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
        </li>
      </ol>

      <!-- Error -->
      <div v-else-if="loadError" class="grid justify-items-start gap-3 px-4 py-8 lg:px-5">
        <p class="text-gray-700 dark:text-gray-300">{{ t('models_ranking.error.text') }}</p>
        <button type="button" class="inline-flex min-h-11 items-center gap-2 rounded-xl border border-gray-300 bg-white px-4 font-semibold text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100" @click="load">
          <ArrowPathIcon class="h-5 w-5" aria-hidden="true" />{{ t('models_ranking.error.retry') }}
        </button>
      </div>

      <!-- Empty search -->
      <div v-else-if="ranked.length === 0" class="grid justify-items-start gap-3 px-4 py-8 lg:px-5">
        <p class="text-[13px] text-gray-600 dark:text-gray-400" data-testid="ranking-empty">{{ emptyText }}</p>
        <button v-if="!searchHitsOnlyWithoutData" type="button" class="inline-flex min-h-11 items-center rounded-xl border border-gray-300 bg-white px-4 font-semibold text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100" @click="resetFilters">
          {{ t('models_ranking.empty.reset') }}
        </button>
      </div>

      <TransitionGroup v-else tag="ol" class="mr-list list-none" move-class="transition-transform duration-300 ease-out motion-reduce:transition-none">
        <ModelRankingRow
          v-for="item in ranked"
          :key="item.key"
          :item="item"
          :axis="axis"
          :sort="sort"
          :expanded="openKey === item.key"
          :in-compare="isInCompare(item.key)"
          :can-add-compare="canAddCompare"
          :href="modelHref(item.model)"
          :price="price"
          :main-value="cost.mainValue"
          :daily-km="needs.dailyKm"
          :fuel="cost.fuel"
          @toggle="openKey = openKey === item.key ? null : item.key"
          @compare="toggleCompare(item.key)"
        />
      </TransitionGroup>
      <p v-if="!loading && !loadError" class="px-4 py-2.5 text-[12.5px] text-gray-500 lg:px-5 dark:text-gray-400">{{ t('models_ranking.list_hint') }}</p>
    </section>

    <!-- Models with a WLTP spec but no driver data yet, collapsed by default -->
    <details
      v-if="withoutData.length"
      class="min-w-0 lg:col-start-2 lg:rounded-2xl lg:border lg:border-gray-200 lg:bg-white dark:lg:border-gray-800 dark:lg:bg-gray-900"
      :open="withoutDataOpen"
      data-testid="without-data"
      @toggle="withoutDataOpen = ($event.target as HTMLDetailsElement).open"
    >
      <summary class="flex min-h-12 cursor-pointer list-none items-center gap-2 px-4 text-[15px] font-semibold text-gray-900 marker:hidden lg:px-5 dark:text-gray-100 [&::-webkit-details-marker]:hidden">
        <ChevronRightIcon class="h-4 w-4 flex-none transition-transform" :class="withoutDataOpen ? 'rotate-90' : ''" aria-hidden="true" />
        {{ t('models_ranking.without_data.title', { count: withoutData.length }) }}
      </summary>
      <div class="grid gap-3 px-4 pb-4 lg:px-5">
        <p class="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[13px] text-gray-600 dark:text-gray-400">
          {{ t('models_ranking.without_data.cta') }}
          <a :href="isAuthenticated ? '/cars' : registerPath" class="inline-flex min-h-9 items-center rounded-lg bg-green-600 px-3 text-[13px] font-semibold text-white hover:bg-green-700">
            {{ t(isAuthenticated ? 'models_ranking.without_data.cta_add_car' : 'models_ranking.without_data.cta_register') }}
          </a>
        </p>
        <ul class="divide-y divide-gray-200 dark:divide-gray-800">
          <li v-for="m in withoutData" :key="modelKey(m)" class="flex min-h-11 items-center gap-3 py-1.5 text-[13.5px]">
            <a :href="modelHref(m)" class="min-w-0 flex-1 truncate font-medium text-gray-900 underline-offset-2 hover:underline dark:text-gray-100">{{ m.modelDisplayName }}</a>
            <span class="whitespace-nowrap tabular-nums text-gray-600 dark:text-gray-400">{{ t('models_ranking.without_data.wltp', { value: formatConsumption(m.avgWltpConsumptionKwhPer100km) }) }}</span>
            <span class="hidden whitespace-nowrap text-[12.5px] text-gray-500 sm:inline dark:text-gray-400">{{ t('models_ranking.row.needs_unrated') }}</span>
          </li>
        </ul>
      </div>
    </details>
  </div>

  <div class="mx-auto grid max-w-7xl gap-7 px-4 pb-32 lg:px-6">
    <ThgBanner v-if="!isAuthenticated && !loading" />

    <section v-if="brands.length" :aria-label="t('models_ranking.brands_title')">
      <h2 class="mb-3 text-[22px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.brands_title') }}</h2>
      <nav class="flex flex-wrap gap-2">
        <a v-for="b in brands" :key="b" :href="`${modelsBaseUrl}/${b}`" class="inline-flex min-h-10 items-center rounded-lg border border-gray-200 bg-white px-3.5 text-sm font-medium text-gray-900 hover:border-green-600 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100">{{ b }}</a>
      </nav>
    </section>

    <section v-if="modelsAz.length" :aria-label="t('models_ranking.az_title')">
      <h2 class="mb-3 text-[22px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.az_title') }}</h2>
      <ul class="columns-2 gap-6 text-sm sm:columns-3 lg:columns-4">
        <li v-for="m in modelsAz" :key="modelKey(m)" class="break-inside-avoid py-1">
          <a :href="modelHref(m)" class="text-gray-700 underline-offset-2 hover:text-green-700 hover:underline dark:text-gray-300 dark:hover:text-green-400">{{ m.modelDisplayName }}</a>
        </li>
      </ul>
    </section>

    <section v-if="!isAuthenticated" class="grid gap-3 rounded-2xl bg-gray-900 p-6 text-white dark:bg-gray-800">
      <h2 class="text-[22px] font-bold tracking-tight">{{ t('models_ranking.cta.title') }}</h2>
      <p class="max-w-[52ch] text-gray-300">{{ t('models_ranking.cta.text') }}</p>
      <a :href="registerPath" class="inline-flex min-h-11 items-center justify-self-start rounded-xl bg-green-600 px-4 font-semibold text-white hover:bg-green-500">{{ t('models_ranking.cta.button') }}</a>
    </section>

    <p v-if="totalLogs > 0" class="text-center text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.footer', { logs: formatNumber(totalLogs) }) }}</p>
  </div>

  <!-- Compare tray -->
  <Transition enter-from-class="translate-y-4 opacity-0" leave-to-class="translate-y-4 opacity-0" enter-active-class="transition duration-200" leave-active-class="transition duration-200">
    <div
      v-if="compareKeys.length"
      class="fixed inset-x-3 z-40 mx-auto flex max-w-[560px] items-center gap-2.5 rounded-2xl bg-gray-900 py-2.5 pl-4 pr-2.5 text-white shadow-2xl dark:bg-gray-100 dark:text-gray-900"
      :class="isAuthenticated ? 'bottom-[calc(4.25rem+env(safe-area-inset-bottom))] md:bottom-4' : 'bottom-[calc(0.75rem+env(safe-area-inset-bottom))]'"
    >
      <p class="min-w-0 flex-1 truncate text-[13.5px]">
        <template v-if="compareNames.length === 1"><b>{{ compareNames[0] }}</b> · {{ t('models_ranking.compare.one_more') }}</template>
        <template v-else>{{ compareNames.join(' · ') }}</template>
      </p>
      <button type="button" class="inline-flex min-h-11 items-center gap-2 rounded-xl bg-green-600 px-4 font-semibold text-white disabled:opacity-50" :disabled="compareKeys.length < 2" @click="startCompare">
        <ArrowsRightLeftIcon class="h-5 w-5" aria-hidden="true" />{{ t('models_ranking.compare.button') }}
      </button>
      <button type="button" class="grid h-11 w-11 place-items-center" :aria-label="t('models_ranking.compare.clear')" @click="clearCompare">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>
  </Transition>

  <footer v-if="!isAuthenticated" class="mx-auto max-w-7xl border-t border-gray-200 px-4 py-8 text-center text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400">
    © {{ currentYear }} EV Monitor ·
    <a :href="registerPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.free_start') }}</a> ·
    <a :href="loginPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.login') }}</a>
  </footer>
  <AssumptionsSheet
    v-if="assumptionsOpen"
    v-model="cost"
    :home-share="homeShare"
    :combustion="combustionMarket"
    :fuel-price-from-api="fuelPriceFromApi"
    @reset="resetCost"
    @close="closeAssumptions"
  />
  <DemoModelsModal />
</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, toRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowPathIcon, ArrowRightIcon, ArrowsRightLeftIcon, ArrowTrendingUpIcon,
  ChartBarIcon, CheckIcon, ChevronRightIcon, MagnifyingGlassIcon, XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'
import {
  getTopModels, getPlatformStats, getCategories, getChargingReferencePrices, getModelsWithoutData,
  type TopModelPreview, type ModelWithoutData, type PlatformStats, type VehicleCategoryItem, type ChargingReferencePrices,
} from '../api/publicModelService'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useMarketRoute, getMarketBasePath } from '../composables/useMarketRoute'
import { useModelRanking, modelKey, RANKING_SORTS } from '../composables/useModelRanking'
import { combustionAllowed } from '../utils/costMix'
import { analytics } from '../services/analytics'
import { useModelsListSeo } from '../composables/useModelsListSeo'
import { buildLadderAxis } from '../utils/ladderScale'
import PublicNav from '../components/shared/PublicNav.vue'
import ThgBanner from '../components/shared/ThgBanner.vue'
import DemoModelsModal from '../components/demo/DemoModelsModal.vue'
import ModelRankingRow from '../components/models/ModelRankingRow.vue'
import NeedsCheckCard from '../components/models/NeedsCheckCard.vue'
import PriorityPicker from '../components/models/PriorityPicker.vue'
import AssumptionsSheet from '../components/models/AssumptionsSheet.vue'

const props = withDefaults(defineProps<{ preview?: boolean }>(), { preview: false })

const { t, te, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { formatNumber, formatDecimal, formatConsumption, consumptionUnitLabel, distanceUnitLabel, currencySymbol, isImperial, unitSystem } = useLocaleFormat()
const { currentMarket, isDE, isEN, isGB, isUS } = useMarketRoute()

const isAuthenticated = computed(() => authStore.isAuthenticated())
const isRedditSource = new URLSearchParams(window.location.search).get('utm_source') === 'reddit'
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const isEnglishPath = computed(() => isEN.value || isGB.value || isUS.value)
const loginPath = computed(() => (isEnglishPath.value ? '/en/login' : '/login'))
const registerPath = computed(() => (isEnglishPath.value ? '/en/register' : '/register'))
const currentYear = new Date().getFullYear()
const asOf = computed(() => new Date().toLocaleDateString(locale.value === 'en' ? 'en-GB' : locale.value, { month: 'long', year: 'numeric' }))

// ── Data ────────────────────────────────────────────────────────────────────
const models = ref<TopModelPreview[]>([])
const modelsWithoutData = ref<ModelWithoutData[]>([])
const platformStats = ref<PlatformStats | null>(null)
const categories = ref<VehicleCategoryItem[]>([])
const referencePrices = ref<ChargingReferencePrices | null>(null)
const loading = ref(true)
const loadError = ref(false)

const {
  sort, category, query, price, cost, homeShare, combustionMarket, applyReferencePrices, resetCost, priority,
  needs, needsSummary, tripOnly, ranked, withoutData, searchHitsOnlyWithoutData, avgWltpDeviationPct,
  compareKeys, canAddCompare, isInCompare, toggleCompare, clearCompare,
} = useModelRanking(models, modelsWithoutData)
watch(currentMarket, m => { combustionMarket.value = combustionAllowed(m) }, { immediate: true })

useModelsListSeo(models, toRef(props, 'preview'), modelsWithoutData)

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [top, stats, cats, prices, noData] = await Promise.all([
      getTopModels(200),
      getPlatformStats().catch(() => null),
      getCategories().catch(() => []),
      getChargingReferencePrices().catch(() => null),
      getModelsWithoutData().catch(() => []),
    ])
    models.value = top
    modelsWithoutData.value = noData
    platformStats.value = stats
    categories.value = cats
    referencePrices.value = prices
    applyReferencePrices(prices, isDE.value)
  } catch (err) {
    console.error('Failed to load models:', err)
    loadError.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ── Hero ────────────────────────────────────────────────────────────────────
const totalLogs = computed(() => models.value.reduce((sum, m) => sum + m.logCount, 0))
const heroFacts = computed(() => {
  const dev = avgWltpDeviationPct.value
  return [
    { label: t('models_ranking.hero.fact_models'), value: formatNumber(models.value.length), highlight: false },
    { label: t('models_ranking.hero.fact_trips'), value: platformStats.value ? formatNumber(platformStats.value.tripCount) : '–', highlight: false },
    { label: t('models_ranking.hero.fact_drivers'), value: platformStats.value ? formatNumber(platformStats.value.userCount) : '–', highlight: false },
    { label: t('models_ranking.hero.fact_wltp'), value: dev != null ? `${dev > 0 ? '+' : ''}${formatDecimal(dev, 0)} %` : '–', highlight: true },
  ]
})

// ── Filters ─────────────────────────────────────────────────────────────────
function categoryLabel(c: VehicleCategoryItem) {
  const key = `models_list.filters.categories.${c.key}`
  return te(key) ? t(key) : c.displayName
}
const categoryChips = computed(() => {
  const count = (key: string | null) => models.value.filter(m => key === null || m.category === key).length
  return [
    { key: null as string | null, label: t('models_ranking.filters.all'), count: count(null) },
    ...categories.value
      .map(c => ({ key: c.key as string | null, label: categoryLabel(c), count: count(c.key) }))
      .filter(c => c.count > 0),
  ]
})

function chipClass(active: boolean, compact = false) {
  return [
    'inline-flex flex-none items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 font-medium',
    compact ? 'min-h-9 text-[13.5px]' : 'min-h-10 text-sm',
    active
      ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
      : 'bg-gray-200/70 text-gray-800 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700',
  ]
}

// ── Assumptions (cost) ──────────────────────────────────────────────────────
const assumptionsOpen = ref(false)
const assumptionsChip = ref<HTMLButtonElement | null>(null)
function openAssumptions() {
  assumptionsOpen.value = true
  analytics.track('assumptions_open')
}
function closeAssumptions() {
  assumptionsOpen.value = false
  assumptionsChip.value?.focus()
}
const fuelPriceFromApi = computed(() => {
  const api = referencePrices.value?.petrolPricePerLiter
  return api != null && cost.value.fuelPricePerLiter != null && Math.abs(cost.value.fuelPricePerLiter - Math.round(api * 100) / 100) < 0.0005
})

// ── Plausible ───────────────────────────────────────────────────────────────
watch(sort, s => analytics.track('ranking_sort', { sort: s }))
let needsEditTimer: ReturnType<typeof setTimeout> | undefined
watch(needs, () => {
  clearTimeout(needsEditTimer)
  needsEditTimer = setTimeout(() => analytics.track('needs_check_edit'), 1000)
}, { deep: true })

function resetFilters() {
  query.value = ''
  category.value = null
  tripOnly.value = false
}

// Search that only hits models without driver data: say so and open that list
const withoutDataOpen = ref(false)
watch(searchHitsOnlyWithoutData, hit => { if (hit) withoutDataOpen.value = true })
const emptyText = computed(() => {
  if (!searchHitsOnlyWithoutData.value) return t('models_ranking.empty.search', { query: query.value.trim() })
  const hits = withoutData.value
  return hits.length === 1
    ? t('models_ranking.empty.search_without_data', { model: hits[0].modelDisplayName, wltp: formatConsumption(hits[0].avgWltpConsumptionKwhPer100km) })
    : t('models_ranking.empty.search_without_data_many', { count: hits.length })
})

// ── Ranking ─────────────────────────────────────────────────────────────────
const openKey = ref<string | null>(null)

// Axis from all models (not the filtered list), so it stays put while filtering.
const axis = computed(() => buildLadderAxis(
  models.value.flatMap(m => [
    m.avgConsumptionKwhPer100km, m.avgWltpConsumptionKwhPer100km, m.minWltpConsumptionKwhPer100km,
    m.maxWltpConsumptionKwhPer100km, m.minRealConsumptionKwhPer100km, m.maxRealConsumptionKwhPer100km,
  ]),
  unitSystem.value.consumptionUnit,
))
const tickDecimals = computed(() => (axis.value.ticks.every(Number.isInteger) ? 0 : 1))
// Ticks are already in the display unit; position them on the same 0-100 scale as the values.
function tickPosition(tick: number) {
  return ((tick - axis.value.min) / (axis.value.max - axis.value.min)) * 100
}
const columnHeads = computed(() => {
  const costUnit = `${currencySymbol.value}/100 ${isImperial.value ? 'mi' : 'km'}`
  return [
    { cls: 'mr-c1', label: t('models_ranking.columns.real'), unit: consumptionUnitLabel() },
    { cls: 'mr-c2', label: t('models_ranking.columns.wltp'), unit: consumptionUnitLabel() },
    { cls: 'mr-c3', label: t('models_ranking.columns.cost'), unit: costUnit },
    { cls: 'mr-c6', label: t('models_ranking.columns.vs', { fuel: t(`models_ranking.assumptions.${cost.value.fuel}`) }), unit: costUnit },
    { cls: 'mr-c4', label: t('models_ranking.columns.range'), unit: distanceUnitLabel() },
  ]
})

const stickyTopClass = computed(() => isAuthenticated.value
  ? 'top-[calc(env(safe-area-inset-top)+var(--top-nav-h,0px))]'
  : 'top-[calc(env(safe-area-inset-top)+70px)]')

function modelHref(m: Pick<TopModelPreview, 'brandDisplayName' | 'modelUrlSlug'>) {
  return `${modelsBaseUrl.value}/${m.brandDisplayName}/${m.modelUrlSlug}`
}

// ── Below the list ──────────────────────────────────────────────────────────
const brands = computed(() => [...new Set(models.value.map(m => m.brandDisplayName))].sort((a, b) => a.localeCompare(b)))
// A to Z also lists the models that only have a WLTP spec so far
const modelsAz = computed(() => [...models.value, ...modelsWithoutData.value].sort((a, b) => a.modelDisplayName.localeCompare(b.modelDisplayName)))

// ── Compare ─────────────────────────────────────────────────────────────────
const compareNames = computed(() => compareKeys.value.map(k => models.value.find(m => modelKey(m) === k)?.modelDisplayName ?? k.replace(/_/g, ' ')))
function startCompare() {
  analytics.track('compare_start', { models: compareKeys.value.length })
  const comparePath = isDE.value ? '/modelle/vergleich' : '/en/models/compare'
  router.push(`${comparePath}?models=${compareKeys.value.join(',')}`)
}
</script>

<style>
/* Row background for the ladder halos (ring and dot), see ConsumptionLadder */
.mr-board { --mr-surface: var(--color-gray-50); }
.dark .mr-board { --mr-surface: var(--color-gray-950); }
@media (min-width: 1024px) {
  .mr-board { --mr-surface: var(--color-white); }
  .dark .mr-board { --mr-surface: var(--color-gray-900); }
}
</style>

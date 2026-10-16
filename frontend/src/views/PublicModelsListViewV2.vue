<template>
<!-- Own background also when signed in: the ranking is a flat page, the app wallpaper
     would show through between hero, filter column and board. -->
<div class="min-h-screen bg-gray-50 dark:bg-gray-950">
  <PublicNav />

  <!-- Hero -->
  <header v-if="!isRedditSource" class="mx-auto grid max-w-7xl gap-6 px-4 pb-5 pt-7 lg:px-6 lg:pb-8 lg:pt-12">
    <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-center">
    <div class="grid gap-4">
      <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.hero.eyebrow', { date: asOf }) }}</p>
      <h1 class="max-w-[18ch] text-balance text-[clamp(30px,7.4vw,52px)] font-bold leading-[1.04] tracking-tight text-gray-900 dark:text-gray-100">
        {{ t('models_ranking.hero.title_start') }} {{ t('models_ranking.hero.title_highlight') }} {{ t('models_ranking.hero.title_end') }}
      </h1>
      <p v-if="totalLogs > 0" class="max-w-[60ch] text-gray-600 dark:text-gray-400">
        {{ t('models_ranking.hero.lede', { logs: formatNumber(totalLogs), drivers: formatNumber(platformStats?.userCount ?? 0) }) }}
      </p>
    </div>
    <!-- Two ways in, one per persona: the answers for me, or the drivers' figures -->
    <nav class="grid gap-2.5" :aria-label="t('ranking_card.title')" data-testid="entry-cards">
      <a v-for="c in entryCards" :key="c.key" :href="c.href" :data-testid="`entry-${c.key}`"
         class="edge-card flex min-h-16 items-center gap-3 rounded-2xl bg-white px-4 py-3 hover:bg-green-50 dark:bg-gray-900 dark:hover:bg-green-950/40"
         @click="onEntry(c.key)">
        <component :is="c.icon" class="h-6 w-6 flex-none text-green-700 dark:text-green-400" aria-hidden="true" />
        <span class="grid min-w-0 flex-1">
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ c.title }}</span>
          <span class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ c.sub }}</span>
        </span>
        <ChevronRightIcon class="h-5 w-5 flex-none text-gray-400" aria-hidden="true" />
      </a>
    </nav>
    </div>
    <dl class="edge-card grid grid-cols-2 gap-[2px] overflow-hidden rounded-2xl bg-gray-900 sm:grid-cols-4 dark:bg-gray-200">
      <div v-for="fact in heroFacts" :key="fact.label" class="flex flex-col-reverse gap-1 bg-white px-3.5 py-3 md:px-5 md:py-4 dark:bg-gray-900">
        <dt class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ fact.label }}</dt>
        <dd class="text-[28px] font-extrabold leading-none tracking-tight tabular-nums md:text-[34px]" :class="fact.highlight ? 'text-orange-700 dark:text-orange-400' : 'text-gray-900 dark:text-gray-100'">{{ fact.value }}</dd>
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

  <!-- Chapter 1: the answers for the reader's driving, free as spans over all models; which 5 fit is behind the gate -->
  <div v-if="!loading && !loadError && models.length" class="mx-auto max-w-7xl pb-6 lg:px-6">
    <ModelsAnswerCard ref="answerCard" v-model="needs" :entries="rankingEntries" :models="models" :price="price" :locked="!isAuthenticated"
                      :petrol-price-per-liter="combustionMarket ? referencePrices?.petrolPricePerLiter ?? null : null" :model-href="modelHref"
                      @price="v => ownPrice = v" @unlocked="onUnlocked" />
  </div>

  <div id="fahrer-messen" class="mx-auto grid max-w-7xl scroll-mt-24 grid-cols-1 gap-[18px] pb-10 lg:px-6">
    <!-- Chapter 2: the drivers' figures, free for everyone. One box: title, search and chips, then the list. -->
    <section class="mr-board grid min-w-0 bg-white pt-6 md:rounded-3xl md:edge-box md:pt-7 dark:bg-gray-900" aria-labelledby="fahrer-messen-t">
      <div class="grid min-w-0 gap-3.5 px-4 md:px-7">
        <div class="grid gap-1">
          <h2 id="fahrer-messen-t" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('models_ranking.box_title') }}</h2>
          <p class="max-w-[70ch] text-[14.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('models_ranking.box_sub') }}</p>
        </div>
        <div class="grid min-w-0 gap-3 lg:flex lg:items-center">
          <label class="relative block lg:w-[320px] lg:flex-none">
            <span class="sr-only">{{ t('models_ranking.filters.search_label') }}</span>
            <MagnifyingGlassIcon class="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            <input
              v-model="query"
              type="search"
              autocomplete="off"
              :placeholder="t('models_ranking.filters.search_placeholder')"
              class="h-12 w-full rounded-xl border border-gray-300 bg-white pl-11 pr-3.5 text-base text-gray-900 placeholder:text-gray-500 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:placeholder:text-gray-400"
            />
          </label>
          <div :class="CHIP_ROW" role="group" :aria-label="t('models_ranking.sort.label')">
            <ChoiceChip v-for="s in RANKING_SORTS" :key="s" :active="sort === s" @click="sort = s">{{ t(`models_ranking.sort.${s}`) }}</ChoiceChip>
          </div>
        </div>
        <div :class="CHIP_ROW" role="group" :aria-label="t('models_ranking.filters.category')">
          <ChoiceChip v-for="c in categoryChips" :key="c.key ?? 'all'" size="sm" :active="category === c.key" @click="category = c.key">
            {{ c.label }}<span class="text-xs tabular-nums opacity-65">{{ c.count }}</span>
          </ChoiceChip>
        </div>
        <!-- Signed in with a trip: only the models that make it with the fewest stops any model needs -->
        <div v-if="fitLabel" :class="CHIP_ROW" role="group" :aria-label="fitLabel">
          <ChoiceChip :active="tripOnly" data-testid="fit-filter" @click="setFit(true)">
            <CheckIcon v-if="tripOnly" class="h-4 w-4" stroke-width="2.5" aria-hidden="true" />{{ fitLabel }}
          </ChoiceChip>
          <ChoiceChip :active="!tripOnly" data-testid="fit-all" @click="setFit(false)">{{ t('models_ranking.fit_all') }}</ChoiceChip>
        </div>
        <p v-if="!loading && !loadError" class="flex flex-wrap items-baseline gap-x-2 text-[13px] text-gray-500 dark:text-gray-400" aria-live="polite" data-testid="ranking-count">
          <span>{{ t('models_ranking.count', { shown: shownCount, total: models.length }) }}</span>
          <button v-if="narrowed" type="button" class="font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400" @click="resetFilters">{{ t('models_ranking.count_reset') }}</button>
        </p>
      </div>

      <!-- Column heads (desktop only; on the phone the bars are labelled in the row).
           .mr-grid sets display itself, so plain `hidden` would lose the cascade. -->
      <div class="mr-grid mr-ruler mt-3 h-[38px] border-b border-gray-200 pr-4 max-lg:!hidden lg:pr-5 dark:border-gray-800" aria-hidden="true">
        <span class="mr-who self-center text-left text-[14px] font-semibold" :class="headClass('mr-who')">{{ t('models_ranking.columns.model') }}</span>
        <span class="mr-ld self-center text-left text-[14px] font-semibold" :class="headClass('mr-ld')">
          {{ t('models_ranking.columns.consumption') }} <span class="font-normal text-gray-500 dark:text-gray-400">{{ consumptionUnitLabel() }}</span>
        </span>
        <span v-for="col in columnHeads" :key="col.cls" :class="[col.cls, col.cls === 'mr-c4' ? 'xl:block' : 'lg:block', headClass(col.cls)]" class="hidden self-center whitespace-nowrap text-right text-[14px] font-semibold leading-tight">
          {{ col.label }}
        </span>
      </div>

      <!-- Loading: skeleton rows -->
      <ol v-if="loading" class="mt-3 list-none border-t border-gray-200 lg:mt-0 lg:border-t-0 dark:border-gray-800" aria-hidden="true">
        <li v-for="n in LIST_PREVIEW" :key="n" class="mr-grid border-b border-gray-200 pr-4 lg:pr-5 dark:border-gray-800">
          <span class="mr-th h-16 self-stretch bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
          <span class="mr-who h-4 w-3/4 rounded bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
          <span class="mr-ld h-2 rounded bg-gray-200 dark:bg-gray-800 animate-pulse"></span>
        </li>
      </ol>

      <!-- Error -->
      <div v-else-if="loadError" class="grid justify-items-start gap-3 px-4 py-8 md:px-7">
        <p class="text-gray-700 dark:text-gray-300">{{ t('models_ranking.error.text') }}</p>
        <button type="button" class="inline-flex min-h-11 items-center gap-2 rounded-xl border border-gray-300 bg-white px-4 font-semibold text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100" @click="load">
          <ArrowPathIcon class="h-5 w-5" aria-hidden="true" />{{ t('models_ranking.error.retry') }}
        </button>
      </div>

      <!-- Empty search -->
      <div v-else-if="ranked.length === 0" class="grid justify-items-start gap-3 px-4 py-8 md:px-7">
        <p class="text-[13px] text-gray-600 dark:text-gray-400" data-testid="ranking-empty">{{ emptyText }}</p>
        <button v-if="!searchHitsOnlyWithoutData" type="button" class="inline-flex min-h-11 items-center rounded-xl border border-gray-300 bg-white px-4 font-semibold text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100" @click="resetFilters">
          {{ t('models_ranking.empty.reset') }}
        </button>
      </div>

      <!-- All rows stay in the DOM (links for search engines); collapsed, rows after the first 8 are only hidden -->
      <TransitionGroup v-else tag="ol" class="mr-list mt-3 list-none border-t border-gray-200 lg:mt-0 lg:border-t-0 dark:border-gray-800" move-class="transition-transform duration-300 ease-out motion-reduce:transition-none">
        <ModelRankingRow
          v-for="(item, i) in ranked"
          :key="item.key"
          :class="{ hidden: collapsed && i >= LIST_PREVIEW }"
          data-testid="ranking-row"
          :item="item"
          :axis="axis"
          :sort="sort"
          :expanded="openKey === item.key"
          :in-compare="isInCompare(item.key)"
          :can-add-compare="canAddCompare"
          :href="modelHref(item.model)"
          :price="price"
          :main-value="cost.mainValue"
          :longest-trip-km="needs.longestTripKm"
          :fuel="cost.fuel"
          :locked="!isAuthenticated"
          @toggle="openKey = openKey === item.key ? null : item.key"
          @compare="toggleCompare(item.key)"
          @unlock="unlockFromRow"
          @assumptions="openAssumptions"
        />
      </TransitionGroup>
      <div v-if="!loading && !loadError" class="grid justify-items-center gap-3 px-4 pb-6 pt-4 md:px-7">
        <button v-if="collapsed" type="button" class="inline-flex min-h-11 items-center gap-1.5 font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400" data-testid="show-all" @click="showAll = true">
          {{ t('models_ranking.show_all', { count: formatNumber(ranked.length) }) }}
          <ChevronDownIcon class="h-4 w-4" aria-hidden="true" />
        </button>
        <p class="flex flex-wrap items-baseline justify-center gap-x-3 gap-y-1 text-[12.5px] text-gray-500 dark:text-gray-400">
          <button ref="assumptionsChip" type="button" class="min-h-9 font-semibold text-green-700 underline underline-offset-2 hover:text-green-800 dark:text-green-400"
                  aria-haspopup="dialog" :aria-expanded="assumptionsOpen" data-testid="assumptions-chip" @click="openAssumptions">{{ t('models_ranking.assumptions_link') }}</button>
          <span>{{ t('models_ranking.list_hint') }}</span>
        </p>
      </div>
    </section>

    <!-- Models with a WLTP spec but no driver data yet, collapsed by default -->
    <details
      v-if="withoutData.length"
      class="min-w-0 lg:rounded-2xl lg:edge-box lg:bg-white dark:lg:bg-gray-900"
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
        <ChoiceChip v-for="b in brands" :key="b" :href="`${modelsBaseUrl}/${b}`">{{ b }}</ChoiceChip>
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

  <footer class="mx-auto max-w-7xl border-t border-gray-200 px-4 py-8 text-center text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400">
    © {{ currentYear }} EV Monitor<template v-if="!isAuthenticated"> ·
    <a :href="registerPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.free_start') }}</a> ·
    <a :href="loginPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.login') }}</a></template>
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
  ChartBarIcon, CheckIcon, ChevronDownIcon, ChevronRightIcon, MagnifyingGlassIcon, UserIcon, XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'
import {
  getTopModels, getPlatformStats, getCategories, getChargingReferencePrices, getModelsWithoutData,
  type TopModelPreview, type ModelWithoutData, type PlatformStats, type VehicleCategoryItem, type ChargingReferencePrices,
} from '../api/publicModelService'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useMarketRoute, getMarketBasePath } from '../composables/useMarketRoute'
import { useModelRanking, modelKey, RANKING_SORTS, type RankingSort } from '../composables/useModelRanking'
import { combustionAllowed } from '../utils/costMix'
import { analytics } from '../services/analytics'
import { useModelsListSeo } from '../composables/useModelsListSeo'
import { buildLadderAxis } from '../utils/ladderScale'
import PublicNav from '../components/shared/PublicNav.vue'
import ThgBanner from '../components/shared/ThgBanner.vue'
import DemoModelsModal from '../components/demo/DemoModelsModal.vue'
import ModelRankingRow from '../components/models/ModelRankingRow.vue'
import ModelsAnswerCard from '../components/models/ModelsAnswerCard.vue'
import AssumptionsSheet from '../components/models/AssumptionsSheet.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'

const props = withDefaults(defineProps<{ preview?: boolean }>(), { preview: false })

const { t, te, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { formatNumber, formatDecimal, formatConsumption, formatDistance, consumptionUnitLabel, distanceUnitLabel, unitSystem } = useLocaleFormat()
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
  sort, category, query, price, ownPrice, cost, homeShare, combustionMarket, applyReferencePrices, resetCost,
  needs, rankingEntries, tripOnly, fitStops, ranked, withoutData, searchHitsOnlyWithoutData, avgWltpDeviationPct,
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

/** A row of chips: scrolls sideways on the phone, wraps from lg */
const CHIP_ROW = '[scrollbar-width:none] [&::-webkit-scrollbar]:hidden -mx-4 flex min-w-0 gap-2 overflow-x-auto px-4 pb-1 pt-0.5 lg:mx-0 lg:flex-wrap lg:overflow-visible lg:px-0'

/** "Schafft meine 600 km mit 1 Stopp": signed in with a trip only; guests see the gate in chapter 1 */
const fitLabel = computed(() => {
  const stops = fitStops.value
  const km = needs.value.longestTripKm
  if (!isAuthenticated.value || stops == null || km == null) return null
  return stops === 0
    ? t('models_ranking.fit_filter_none', { km: formatDistance(km) })
    : t('models_ranking.fit_filter', { km: formatDistance(km), stops: t('models_ranking.row.stops_word', { count: formatNumber(stops) }, stops) })
})
function setFit(on: boolean) {
  if (tripOnly.value === on) return
  tripOnly.value = on
  if (on && fitStops.value != null) analytics.track('ranking_fit_filter', { stops: fitStops.value })
}

// ── Chapter 1: the answer card and the list point at each other ─────────────
const answerCard = ref<InstanceType<typeof ModelsAnswerCard> | null>(null)
const entryCards = computed(() => [
  { key: 'fit', href: '#passt-zu-dir', icon: UserIcon, title: t('ranking_card.entry_fit_title'), sub: t('ranking_card.entry_fit_sub') },
  { key: 'lean', href: '#fahrer-messen', icon: ChartBarIcon, title: t('ranking_card.entry_lean_title'), sub: t('ranking_card.entry_lean_sub') },
])
function onEntry(card: string) {
  analytics.track('ranking_entry_card', { card })
}
/** "Meine Zahlen" in an open row: the one gate sits in the answer card */
function unlockFromRow() {
  answerCard.value?.showGate()
}
/** The gate confirmed a code: the top 5 and the rows' figures follow through the auth store */
function onUnlocked() {
  analytics.track('ranking_unlocked', { models: rankingEntries.value.length })
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
watch(sort, s => {
  analytics.track('ranking_sort', { sort: s })
  // a new order shows every model
  showAll.value = true
})
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

// First 8 rows, then "Alle 61 Modelle". Search, filters and a sort change show every hit.
const LIST_PREVIEW = 8
const showAll = ref(false)
const narrowed = computed(() => query.value.trim() !== '' || category.value !== null || tripOnly.value)
const collapsed = computed(() => !showAll.value && !narrowed.value && ranked.value.length > LIST_PREVIEW)
const shownCount = computed(() => collapsed.value ? LIST_PREVIEW : ranked.value.length)

// Axis from all models (not the filtered list), so it stays put while filtering.
const axis = computed(() => buildLadderAxis(
  models.value.flatMap(m => [
    m.avgConsumptionKwhPer100km, m.avgWltpConsumptionKwhPer100km, m.minWltpConsumptionKwhPer100km,
    m.maxWltpConsumptionKwhPer100km, m.minRealConsumptionKwhPer100km, m.maxRealConsumptionKwhPer100km,
  ]),
  unitSystem.value.consumptionUnit,
))
// Units live in the cells next to the numbers, the heads only name the column
const columnHeads = computed(() => [
  { cls: 'mr-c3', label: t('models_ranking.columns.cost_per', { unit: distanceUnitLabel() }) },
  { cls: 'mr-c4', label: t('models_ranking.columns.range') },
])
// The sorted column carries the accent; the chip names the sort, the head shows where it acts
const SORT_COLUMN: Record<RankingSort, string> = { efficient: 'mr-ld', wltp: 'mr-ld', winter: 'mr-ld', range: 'mr-c4', data: 'mr-who' }
function headClass(cls: string) {
  return SORT_COLUMN[sort.value] === cls ? 'text-green-700 dark:text-green-400' : 'text-gray-700 dark:text-gray-300'
}

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

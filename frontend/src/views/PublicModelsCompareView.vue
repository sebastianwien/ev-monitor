<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-6xl mx-auto px-4 py-8">
      <!-- Breadcrumb -->
      <nav class="text-sm text-gray-500 dark:text-gray-400 mb-6">
        <a href="/modelle" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.breadcrumb_models') }}</a>
        <span class="mx-2">›</span>
        <span class="text-gray-900 dark:text-gray-100">{{ t('compare.breadcrumb_current') }}</span>
      </nav>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- Error / invalid params -->
      <div v-else-if="error || items.length < 2" class="text-center py-20">
        <TruckIcon class="h-16 w-16 text-gray-300 dark:text-gray-600 mx-auto mb-4" />
        <h1 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('compare.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ error || t('compare.error_min_models') }}</p>
        <a href="/modelle" class="bg-green-600 text-white px-6 py-3 rounded-sm hover:bg-green-700">
          {{ t('compare.breadcrumb_models') }}
        </a>
      </div>

      <template v-else>
        <!-- Header -->
        <div class="bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 p-6 mb-6">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">
            {{ items.map(i => i.model.modelDisplayName).join(' vs. ') }}
          </h1>
          <p class="text-gray-500 dark:text-gray-400 text-sm">
            {{ t('compare.subtitle') }}
          </p>
          <a :href="modelsBaseUrl" class="inline-flex items-center gap-1.5 text-sm text-green-600 hover:underline mt-3">
            <ArrowLeftIcon class="h-4 w-4" />
            {{ t('compare.change_models') }}
          </a>
        </div>

        <!-- Sentinel for IntersectionObserver (mobile sticky header trigger) -->
        <div ref="stickysentinel" class="block md:hidden h-0"></div>

        <!-- Fixed floating header (shown when sentinel scrolls out of view) -->
        <Transition name="slide-down">
          <div v-if="floatingHeaderVisible"
               class="block md:hidden fixed left-0 right-0 z-40 bg-white dark:bg-gray-900 border-b-2 border-gray-100 dark:border-gray-800 px-4 py-2 shadow-[3px_3px_0_rgba(0,0,0,0.25)] dark:shadow-[3px_3px_0_rgba(255,255,255,0.25)]"
               :style="{ top: floatingHeaderTop }">
            <div class="grid gap-3" :class="gridCols">
              <div v-for="item in items" :key="item.key" class="text-center text-xs font-bold text-gray-900 dark:text-gray-100 leading-tight truncate">{{ item.model.modelDisplayName }}</div>
            </div>
          </div>
        </Transition>

        <!-- Comparison: Mobile Card Layout (label above the values) -->
        <div class="block md:hidden bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 mb-6">
          <div class="border-b-2 border-gray-100 dark:border-gray-800 px-4 py-3">
            <div class="grid gap-2" :class="gridCols">
              <a v-for="item in items" :key="item.key" :href="modelHref(item)"
                 class="text-center text-xs font-bold text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 leading-tight block">
                {{ item.model.modelDisplayName }}
              </a>
            </div>
          </div>
          <div v-for="row in rows" :key="row.key" class="border-t border-gray-100 dark:border-gray-800 px-4 py-3" data-testid="compare-row">
            <div class="text-xs mb-2 text-center text-gray-500 dark:text-gray-400">{{ row.label }}</div>
            <div class="grid gap-2" :class="gridCols">
              <div v-for="(cell, i) in row.cells" :key="i" class="text-center text-sm" data-testid="compare-cell">
                <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">
                  <span v-if="cell.better" class="mr-1 inline-block h-2 w-2 rounded-full bg-green-600 align-middle dark:bg-green-400" data-testid="compare-better"></span><span v-if="cell.better" class="sr-only">{{ t('compare.legend_better') }}: </span>{{ cell.value }}
                </span>
                <span v-for="line in cell.sub" :key="line" class="block text-xs text-gray-500 dark:text-gray-400">{{ line }}</span>
              </div>
            </div>
          </div>
          <div class="px-4 py-3 border-t border-gray-100 dark:border-gray-800 flex flex-wrap gap-4 text-xs text-gray-500 dark:text-gray-400">
            <span class="flex items-center gap-1.5">
              <span class="w-2 h-2 rounded-full bg-green-600 dark:bg-green-400 inline-block"></span>
              {{ t('compare.legend_better') }}
            </span>
          </div>
        </div>

        <!-- Comparison: Desktop Table -->
        <div class="hidden md:block bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden mb-6">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b-2 border-gray-100 dark:border-gray-800">
                <th class="w-44 py-4 px-4"></th>
                <th v-for="item in items" :key="item.key" class="py-4 px-3 text-center">
                  <a :href="modelHref(item)" class="font-bold text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 transition-colors block">
                    {{ item.model.modelDisplayName }}
                  </a>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.key" class="border-t border-gray-100 dark:border-gray-800">
                <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-medium">{{ row.label }}</td>
                <td v-for="(cell, i) in row.cells" :key="i" class="px-3 py-3 text-center">
                  <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">
                    <span v-if="cell.better" class="mr-1.5 inline-block h-2 w-2 rounded-full bg-green-600 align-middle dark:bg-green-400"></span><span v-if="cell.better" class="sr-only">{{ t('compare.legend_better') }}: </span>{{ cell.value }}
                  </span>
                  <span v-for="line in cell.sub" :key="line" class="block text-xs text-gray-500 dark:text-gray-400">{{ line }}</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="px-4 py-3 border-t border-gray-100 dark:border-gray-800 flex flex-wrap gap-4 text-xs text-gray-500 dark:text-gray-400">
            <span class="flex items-center gap-1.5">
              <span class="w-2 h-2 rounded-full bg-green-600 dark:bg-green-400 inline-block"></span>
              {{ t('compare.legend_better') }}
            </span>
          </div>
        </div>

        <!-- Links to individual pages -->
        <div class="bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 p-5 mb-6">
          <p class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('compare.detail_pages') }}</p>
          <div class="flex flex-wrap gap-3">
            <a v-for="item in items" :key="item.key"
               :href="modelHref(item)"
               class="flex items-center gap-1.5 text-sm text-green-600 dark:text-green-400 hover:underline">
              <TruckIcon class="h-4 w-4" />
              {{ item.model.modelDisplayName }} →
            </a>
          </div>
        </div>

        <!-- CTA, guests only -->
        <div v-if="!isAuthenticated" class="bg-gradient-to-br from-green-600 to-green-700 rounded-sm p-6 text-white">
          <h2 class="text-xl font-bold mb-2">{{ t('compare.cta_title') }}</h2>
          <p class="text-green-100 text-sm mb-4">
            {{ t('compare.cta_desc') }}
          </p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath" class="bg-white text-green-700 font-semibold px-4 py-2 rounded-sm hover:bg-green-50 transition-colors text-sm">
              {{ t('compare.cta_register') }}
            </a>
            <a :href="loginPath" class="border border-white text-white px-4 py-2 rounded-sm hover:bg-green-600 transition-colors text-sm">
              {{ t('compare.cta_login') }}
            </a>
          </div>
        </div>
      </template>
    </main>

    <footer class="max-w-6xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 dark:border-gray-800 text-sm text-gray-500 dark:text-gray-400 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/modelle" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.all_models') }}</a>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { getTopModels, getChargingReferencePrices, type TopModelPreview } from '../api/publicModelService'
import { TruckIcon, ArrowLeftIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import { useAuthStore } from '../stores/auth'
import { useTickerState } from '../composables/useTickerState'
import { useMarketRoute, getMarketBasePath } from '../composables/useMarketRoute'
import { useModelRanking, MAX_COMPARE, type RankedModel } from '../composables/useModelRanking'
import { useRankedModelText } from '../composables/useRankedModelText'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { combustionAllowed } from '../utils/costMix'
import { betterMarks } from '../utils/compareModels'

const { t, locale } = useI18n()
const { distanceUnitLabel } = useLocaleFormat()
const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated())
const { tickerHasItems, tickerCollapsed } = useTickerState()
const { currentMarket, isDE } = useMarketRoute()
const floatingHeaderTop = computed(() => {
  if (!isAuthenticated.value) return '0px'
  if (tickerHasItems.value && !tickerCollapsed.value) return '90px'
  return '58px'
})
const registerPath = computed(() => locale.value === 'en' ? '/en/register' : '/register')
const loginPath = computed(() => locale.value === 'en' ? '/en/login' : '/login')
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))

const route = useRoute()
const loading = ref(true)

// ── Floating sticky header (IntersectionObserver) ─────────────────────────────
const stickysentinel = ref<HTMLElement | null>(null)
const floatingHeaderVisible = ref(false)
let observer: IntersectionObserver | null = null

watch(stickysentinel, (el) => {
  observer?.disconnect()
  if (!el) return
  observer = new IntersectionObserver(
    ([entry]) => { floatingHeaderVisible.value = !entry.isIntersecting },
    { threshold: 0, rootMargin: '0px' }
  )
  observer.observe(el)
})

onUnmounted(() => observer?.disconnect())
const error = ref<string | null>(null)
const currentYear = new Date().getFullYear()

const modelKeys = computed((): string[] => {
  const raw = route.query.models as string | undefined
  if (!raw) return []
  return raw.split(',').slice(0, MAX_COMPARE).map(s => s.trim()).filter(Boolean)
})

// ── Data: the same ranking as /modelle, so every figure matches the list ──────
const models = ref<TopModelPreview[]>([])
const { ranked, cost, needs, combustionMarket, applyReferencePrices } = useModelRanking(models)
watch(currentMarket, m => { combustionMarket.value = combustionAllowed(m) }, { immediate: true })

onMounted(async () => {
  if (modelKeys.value.length < 2) {
    error.value = t('compare.error_need_two')
    loading.value = false
    return
  }
  try {
    const [top, prices] = await Promise.all([
      getTopModels(200),
      getChargingReferencePrices().catch(() => null),
    ])
    models.value = top
    applyReferencePrices(prices, isDE.value)
  } catch {
    error.value = t('compare.error_loading')
  } finally {
    loading.value = false
  }
})

/** The compared models in the link's order; keys match regardless of case (older shared links) */
const items = computed<RankedModel[]>(() => modelKeys.value
  .map(k => ranked.value.find(r => r.key.toLowerCase() === k.toLowerCase()))
  .filter((r): r is RankedModel => r != null))

function modelHref(item: RankedModel) {
  return `${modelsBaseUrl.value}/${item.model.brandDisplayName}/${item.model.modelUrlSlug}`
}
const gridCols = computed(() => items.value.length === 3 ? 'grid-cols-3' : 'grid-cols-2')

// ── Rows: the ranking row's cells, then what the drivers measure ──────────────
// One text set per compare slot (at most MAX_COMPARE); a slot is only read while it holds a model.
const texts = Array.from({ length: MAX_COMPARE }, (_, i) =>
  useRankedModelText(() => items.value[i], () => needs.value.longestTripKm, () => cost.value.fuel))

interface Cell { value: string; sub: string[]; better: boolean }
interface Row { key: string; label: string; cells: Cell[] }

const round = (v: number | null | undefined, step: number) => v != null ? Math.round(v / step) * step : null
/** The marked figures, rounded as shown: lower consumption and cost, more range */
const marks = computed(() => ({
  consumption: betterMarks(items.value.map(r => round(r.model.avgConsumptionKwhPer100km, 0.1)), true),
  cost: betterMarks(items.value.map(r => round(r.costPer100kmEur, 0.01)), true),
  range: betterMarks(items.value.map(r => round(r.model.typicalRangeMaxKm ?? r.model.realRangeKm, 10)), false),
}))

const FACT_ORDER = ['consumption', 'winter', 'highway', 'wltp', 'battery', 'data']

const rows = computed<Row[]>(() => {
  const slots = items.value.map((_, i) => texts[i])
  const out: Row[] = [
    {
      key: 'consumption',
      label: t('models_ranking.columns.consumption'),
      cells: slots.map((x, i) => ({
        value: `${x.realText.value} ${x.consumptionUnitLabel()}`,
        sub: [`${t('models_ranking.columns.wltp')} ${x.wltpText.value}`],
        better: marks.value.consumption[i],
      })),
    },
    {
      key: 'cost',
      label: t('models_ranking.columns.cost_per', { unit: distanceUnitLabel() }),
      cells: slots.map((x, i) => ({
        value: `${t('models_ranking.row.electricity')} ${x.electricityCost.value}`,
        sub: [x.combustionCaption.value, x.driversPrice.value ?? ''].filter(Boolean),
        better: marks.value.cost[i],
      })),
    },
    {
      key: 'range',
      label: t('models_ranking.columns.range'),
      cells: slots.map((x, i) => ({
        value: `${x.rangeValue.value} ${distanceUnitLabel()}`,
        sub: x.ladderLine.value ? [x.ladderLine.value] : [],
        better: marks.value.range[i],
      })),
    },
  ]
  for (const key of FACT_ORDER) {
    const values = slots.map(x => x.facts.value.find(f => f.key === key)?.value ?? null)
    if (values.every(v => v == null)) continue
    out.push({
      key,
      label: key === 'consumption' ? t('compare.row_seasons') : t(`models_ranking.detail.fact_${key}`),
      cells: values.map(v => ({ value: v ?? '–', sub: [], better: false })),
    })
  }
  return out
})

const BASE_URL = 'https://ev-monitor.net'

useHead(computed(() => {
  const names = items.value.map(i => i.model.modelDisplayName).join(' vs. ')
  const title = names ? `${names} – ${t('compare.breadcrumb_current')} | EV Monitor` : t('compare.head_title_fallback')
  const description = t('compare.head_desc', { names })
  const canonical = `${BASE_URL}${route.fullPath}`
  return {
    title,
    meta: [
      { name: 'description', content: description },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonical },
      { property: 'og:image', content: `${BASE_URL}/og-image.png` },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: title },
      { name: 'twitter:description', content: description },
    ],
    link: [{ rel: 'canonical', href: canonical }],
  }
}))
</script>

<style scoped>
.slide-down-enter-active, .slide-down-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-down-enter-from, .slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>

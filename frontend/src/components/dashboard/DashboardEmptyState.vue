<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  BoltIcon,
  PencilSquareIcon,
  ArrowDownTrayIcon,
  ArrowPathIcon,
  SparklesIcon,
  ChevronRightIcon,
} from '@heroicons/vue/24/outline'
import { getModelStatsByEnum, type PublicModelStats } from '../../api/publicModelService'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

const props = defineProps<{
  car: { brand: string; model: string; [key: string]: unknown }
}>()

const { t } = useI18n()
// /erfassen, /imports, /upgrade are auth-required DE routes with no /en equivalent
const router = useRouter()
const { formatConsumption, consumptionUnitLabel, formatCostPerKwh } = useLocaleFormat()

const loadingStats = ref(true)
const communityStats = ref<PublicModelStats | null>(null)

// Min. 5 trips required before showing community data - avoids misleading single-outlier display
const hasCommunityData = computed(() =>
  communityStats.value !== null
  && (communityStats.value.logCount ?? 0) >= 5
  && communityStats.value.avgConsumptionKwhPer100km !== null
)

const worstWltp = computed<number | null>(() => {
  if (!communityStats.value?.wltpVariants.length) return null
  const values = communityStats.value.wltpVariants
    .map(v => v.wltpConsumptionKwhPer100km)
    .filter((v): v is number => v !== null && v !== undefined)
  return values.length ? Math.max(...values) : null
})

const deltaLabel = computed(() => {
  const real = communityStats.value?.avgConsumptionKwhPer100km
  const wltp = worstWltp.value
  if (!real || !wltp) return ''
  const delta = ((real - wltp) / wltp) * 100
  return `${delta >= 0 ? '+' : ''}${delta.toFixed(0)}%`
})

const deltaBadgeClass = computed(() => {
  const real = communityStats.value?.avgConsumptionKwhPer100km
  const wltp = worstWltp.value
  if (!real || !wltp) return ''
  return real > wltp
    ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300'
    : 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
})

const modelLabel = computed(() =>
  communityStats.value?.modelDisplayName
  ?? props.car.model.replace(/_/g, ' ').toLowerCase()
      .split(' ').map((w: string) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
)

const importSources = ['Sprit-Monitor', 'CSV', 'Tronity', 'go-e Charger']

onMounted(async () => {
  try {
    communityStats.value = await getModelStatsByEnum(props.car.model)
  } catch {
    // fail silently - show pioneer state
  } finally {
    loadingStats.value = false
  }
})
</script>

<template>
  <div class="max-w-lg mx-auto px-4 py-8 space-y-5">

    <!-- Block 1: Community Preview -->
    <div class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">

      <!-- Loading skeleton -->
      <div v-if="loadingStats" class="p-5 space-y-3 animate-pulse">
        <div class="h-3 w-36 bg-gray-200 dark:bg-gray-700 rounded-full"></div>
        <div class="h-10 w-28 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
        <div class="h-3 w-48 bg-gray-200 dark:bg-gray-700 rounded-full"></div>
      </div>

      <!-- Has enough community data (>= 5 trips with consumption) -->
      <template v-else-if="hasCommunityData && communityStats">
        <div class="bg-gradient-to-br from-green-50 to-white dark:from-green-900/20 dark:to-gray-800/0 px-5 pt-5 pb-4">
          <p class="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-3">
            {{ t('dashboard.empty_community_title', { model: communityStats.modelDisplayName }) }}
          </p>
          <div class="flex items-baseline gap-2 flex-wrap">
            <span class="text-4xl font-bold tabular-nums text-gray-900 dark:text-gray-100">
              {{ formatConsumption(communityStats.avgConsumptionKwhPer100km!) }}
            </span>
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ consumptionUnitLabel() }}</span>
            <span v-if="worstWltp && deltaLabel" :class="deltaBadgeClass"
                  class="text-xs font-semibold px-2 py-0.5 rounded-full">
              WLTP {{ formatConsumption(worstWltp) }} &middot; {{ deltaLabel }}
            </span>
          </div>
        </div>

        <div class="grid grid-cols-3 divide-x divide-gray-100 dark:divide-gray-700 border-t border-gray-100 dark:border-gray-700">
          <div class="p-3 text-center">
            <p class="text-base font-bold tabular-nums text-gray-900 dark:text-gray-100">
              {{ communityStats.logCount.toLocaleString() }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.empty_community_trips') }}</p>
          </div>
          <div class="p-3 text-center">
            <p class="text-base font-bold tabular-nums text-gray-900 dark:text-gray-100">
              {{ communityStats.uniqueContributors }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.empty_community_drivers') }}</p>
          </div>
          <div class="p-3 text-center">
            <p class="text-base font-bold tabular-nums text-gray-900 dark:text-gray-100">
              {{ communityStats.avgCostPerKwh ? formatCostPerKwh(communityStats.avgCostPerKwh) : '-' }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.empty_community_avg_cost') }}</p>
          </div>
        </div>

        <p class="px-5 py-3 text-sm text-gray-600 dark:text-gray-400 border-t border-gray-100 dark:border-gray-700">
          {{ t('dashboard.empty_community_cta') }}
        </p>
      </template>

      <!-- Pioneer state: no data or below threshold -->
      <div v-else class="p-6 text-center">
        <SparklesIcon class="h-10 w-10 mx-auto text-amber-400 mb-3" />
        <h3 class="font-bold text-gray-900 dark:text-gray-100 text-lg mb-2">
          {{ t('dashboard.empty_pioneer_title') }}
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
          {{ t('dashboard.empty_pioneer_desc', { model: modelLabel }) }}
        </p>
        <div class="inline-flex items-center gap-1.5 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-amber-700 dark:text-amber-300 text-xs font-medium px-3 py-1.5 rounded-full">
          <BoltIcon class="h-3.5 w-3.5 shrink-0" />
          {{ t('dashboard.empty_pioneer_coins') }}
        </div>
      </div>
    </div>

    <!-- Block 2: Start Options -->
    <div class="space-y-2">
      <p class="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 px-1 pb-1">
        {{ t('dashboard.empty_start_title') }}
      </p>

      <!-- Manual entry -->
      <button
        @click="router.push('/erfassen')"
        class="w-full flex items-center gap-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-green-400 dark:hover:border-green-600 hover:bg-green-50 dark:hover:bg-green-900/10 transition-colors text-left"
      >
        <div class="shrink-0 bg-green-100 dark:bg-green-900/30 rounded-lg p-2.5">
          <PencilSquareIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-semibold text-sm text-gray-900 dark:text-gray-100">{{ t('dashboard.empty_manual_title') }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('dashboard.empty_manual_desc') }}</p>
        </div>
        <ChevronRightIcon class="h-4 w-4 text-gray-400 shrink-0" />
      </button>

      <!-- Import -->
      <button
        @click="router.push('/imports')"
        class="w-full flex items-center gap-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-indigo-400 dark:hover:border-indigo-600 hover:bg-indigo-50 dark:hover:bg-indigo-900/10 transition-colors text-left"
      >
        <div class="shrink-0 bg-indigo-100 dark:bg-indigo-900/30 rounded-lg p-2.5">
          <ArrowDownTrayIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-semibold text-sm text-gray-900 dark:text-gray-100">{{ t('dashboard.empty_import_title') }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('dashboard.empty_import_desc') }}</p>
          <div class="flex flex-wrap gap-1 mt-1.5">
            <span
              v-for="source in importSources"
              :key="source"
              class="text-[10px] bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 border border-indigo-100 dark:border-indigo-800 px-1.5 py-0.5 rounded-full leading-none"
            >{{ source }}</span>
          </div>
        </div>
        <ChevronRightIcon class="h-4 w-4 text-gray-400 shrink-0" />
      </button>

      <!-- AutoSync Premium -->
      <button
        @click="router.push('/upgrade')"
        class="w-full flex items-center gap-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-amber-400 dark:hover:border-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/10 transition-colors text-left"
      >
        <div class="shrink-0 bg-amber-100 dark:bg-amber-900/30 rounded-lg p-2.5">
          <ArrowPathIcon class="h-5 w-5 text-amber-600 dark:text-amber-400" />
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <p class="font-semibold text-sm text-gray-900 dark:text-gray-100">{{ t('dashboard.empty_autosync_title') }}</p>
            <span class="text-[10px] bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 font-semibold px-1.5 py-0.5 rounded-full leading-none">Premium</span>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('dashboard.empty_autosync_desc') }}</p>
          <p class="text-xs text-amber-600 dark:text-amber-400 font-medium mt-0.5">
            {{ t('dashboard.empty_autosync_price', { price: t('upgrade.price_monthly') }) }}
          </p>
        </div>
        <ChevronRightIcon class="h-4 w-4 text-gray-400 shrink-0" />
      </button>
    </div>

  </div>
</template>

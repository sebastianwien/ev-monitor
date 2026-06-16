<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SparklesIcon, LockClosedIcon } from '@heroicons/vue/24/outline'
import { sumPhantomKwh, phantomEur } from '../../utils/phantomDrain'

// Locked-state teaser for the energy-split donut (DashboardInsights), shown to users
// without the analytics entitlement. The phantom-cost figure is FE-derivable from data
// the user already has, so we show it openly as the hook - the full breakdown and
// visualisation behind the blur is the paid product. The blurred shape is static and
// carries no real data, so there is nothing to bypass.
const props = defineProps<{ entries: any[] }>()
const { t } = useI18n()

const phantomCostEur = computed(() => phantomEur(sumPhantomKwh(props.entries)))

function fmt1(n: number): string {
  return n.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}
</script>

<template>
  <div class="relative overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
    <!-- Blurred preview of the energy split (static shape, no real data) -->
    <div class="pointer-events-none select-none blur-[5px] opacity-70 p-4 md:p-5" aria-hidden="true">
      <div class="flex items-center gap-4 md:gap-6">
        <div
          class="w-32 h-32 flex-shrink-0 rounded-full"
          style="background: conic-gradient(#10b981 0 62%, #f59e0b 62% 79%, #9ca3af 79% 100%); -webkit-mask: radial-gradient(transparent 53%, #000 54%); mask: radial-gradient(transparent 53%, #000 54%);"
        ></div>
        <div class="flex-1 space-y-4">
          <div class="h-3 w-3/4 rounded-full bg-emerald-300 dark:bg-emerald-500/60"></div>
          <div class="h-3 w-2/5 rounded-full bg-amber-300 dark:bg-amber-500/60"></div>
          <div class="h-3 w-1/4 rounded-full bg-gray-300 dark:bg-gray-600"></div>
        </div>
      </div>
    </div>

    <!-- Overlay: real hook number + single CTA -->
    <div class="absolute inset-0 flex flex-col items-center justify-center text-center px-5 bg-white/55 dark:bg-gray-900/55">
      <div class="flex items-center gap-1.5 text-amber-600 dark:text-amber-400 mb-1">
        <LockClosedIcon class="w-4 h-4" />
        <span class="text-[11px] font-semibold uppercase tracking-wide">{{ t('dashboard.insights_teaser_badge') }}</span>
      </div>
      <p v-if="phantomCostEur > 0.05" class="text-3xl font-bold text-gray-900 dark:text-white tabular-nums">≈ {{ fmt1(phantomCostEur) }}&nbsp;€</p>
      <p class="text-sm text-gray-600 dark:text-gray-300 mt-1 max-w-xs">{{ t('dashboard.insights_teaser_body') }}</p>
      <router-link
        to="/upgrade"
        class="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-amber-500 hover:bg-amber-600 px-4 py-2 text-sm font-semibold text-white transition-colors"
      >
        <SparklesIcon class="w-4 h-4" />
        {{ t('dashboard.insights_teaser_cta') }}
      </router-link>
    </div>
  </div>
</template>

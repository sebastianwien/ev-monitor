<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SparklesIcon, LockClosedIcon } from '@heroicons/vue/24/outline'
import { sumPhantomKwh, phantomEur } from '../../utils/phantomDrain'
import { purchasesAvailable } from '../../utils/iapPolicy'
import { useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'

// Upsell-Teaser komplett ausblenden, wo kein Kauf moeglich ist (native App, Guideline 3.1.1).
const showUpgrade = purchasesAvailable()

// Locked-state teaser for the energy-split donut (DashboardInsights), shown to users
// without the analytics entitlement. Deliberately crisp (not a blurred fake chart): a
// clean representative donut with a lock, plus the real phantom-cost figure as the hook.
const props = defineProps<{ entries: any[] }>()
const { t } = useI18n()

const phantomCostEur = computed(() => phantomEur(sumPhantomKwh(props.entries)))

// Tesla-Fahrer fehlt nur die Auswertung, nicht die Datenquelle - sie landen direkt
// auf dem Supporter-Pack statt auf der AutoSync-Preistabelle.
const upsellTarget = useAnalyticsUpsellTarget()

function fmt1(n: number): string {
  return n.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

// Static representative donut (circumference 326.7 for r=52). Driven / phantom / loss.
const C = 326.7
const driven = C * 0.62
const phantom = C * 0.17
const loss = C * 0.21
</script>

<template>
  <div v-if="showUpgrade" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 md:p-5">
    <div class="flex items-center gap-4 md:gap-6">
      <!-- Crisp representative donut with a lock badge -->
      <div class="relative flex-shrink-0">
        <svg viewBox="0 0 128 128" class="w-24 h-24 md:w-28 md:h-28 -rotate-90" aria-hidden="true">
          <circle cx="64" cy="64" r="52" fill="none" stroke="#10b981" stroke-width="20"
            :stroke-dasharray="`${driven} ${C - driven}`" stroke-dashoffset="0" />
          <circle cx="64" cy="64" r="52" fill="none" stroke="#f59e0b" stroke-width="20"
            :stroke-dasharray="`${phantom} ${C - phantom}`" :stroke-dashoffset="-driven" />
          <circle cx="64" cy="64" r="52" fill="none" stroke="#9ca3af" stroke-width="20"
            :stroke-dasharray="`${loss} ${C - loss}`" :stroke-dashoffset="-(driven + phantom)" />
        </svg>
        <div class="absolute inset-0 flex items-center justify-center">
          <span class="flex items-center justify-center w-9 h-9 rounded-full bg-white dark:bg-gray-800 shadow ring-1 ring-gray-200 dark:ring-gray-600">
            <LockClosedIcon class="w-4 h-4 text-amber-500 dark:text-amber-400" />
          </span>
        </div>
      </div>

      <!-- Hook + CTA -->
      <div class="min-w-0 flex-1">
        <p class="text-[11px] font-semibold uppercase tracking-wide text-amber-600 dark:text-amber-400">{{ t('dashboard.insights_teaser_badge') }}</p>
        <p v-if="phantomCostEur > 0.05" class="text-2xl font-bold text-gray-900 dark:text-white tabular-nums leading-tight">≈ {{ fmt1(phantomCostEur) }}&nbsp;€</p>
        <p class="text-sm text-gray-600 dark:text-gray-300 mt-0.5">{{ t('dashboard.insights_teaser_body') }}</p>
        <router-link
          :to="upsellTarget"
          class="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-amber-500 hover:bg-amber-600 px-4 py-2 text-sm font-semibold text-white transition-colors"
        >
          <SparklesIcon class="w-4 h-4" />
          {{ t('dashboard.insights_teaser_cta') }}
        </router-link>
      </div>
    </div>
  </div>
</template>

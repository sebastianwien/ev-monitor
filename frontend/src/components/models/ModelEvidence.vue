<template>
  <ModelBox id="belege" aria-labelledby="evidence-title" class="scroll-mt-28" data-testid="model-evidence">
    <div class="grid gap-1">
      <h2 id="evidence-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ title }}</h2>
    </div>
    <div class="grid gap-8 md:grid-cols-2 md:gap-12">
      <!-- Range: one scale from zero for all bars, the manufacturer's figure as grey reference -->
      <figure v-if="evidence.range" class="m-0 grid content-start gap-3 max-md:edge-card max-md:rounded-2xl max-md:p-4" data-testid="evidence-range">
        <figcaption><h3 class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.page.range_title') }}</h3></figcaption>
        <div class="grid gap-2.5">
          <div v-for="bar in evidence.range.bars" :key="bar.key" class="grid grid-cols-[72px_minmax(0,1fr)_auto] items-center gap-3 text-[14px]">
            <span class="text-gray-600 dark:text-gray-400">{{ bar.label }}</span>
            <span class="block h-4 overflow-hidden rounded-r-md bg-gray-100 dark:bg-gray-800" aria-hidden="true">
              <span class="block h-full rounded-r-md motion-safe:transition-[width] motion-safe:duration-700" :class="BAR_TONE[bar.key]" :style="{ width: `${bar.value / rangeMax * 100}%` }"></span>
            </span>
            <b class="min-w-[64px] text-right font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ bar.text }}</b>
          </div>
        </div>
        <p class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ evidence.range.note }}</p>
      </figure>

      <!-- Route types as the difference to their own mean: green bars to the left are thriftier, amber to the right thirstier -->
      <figure v-if="evidence.routes" class="m-0 grid content-start gap-3 max-md:edge-card max-md:rounded-2xl max-md:p-4" data-testid="evidence-routes">
        <figcaption><h3 class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.page.routes_title') }}</h3></figcaption>
        <div class="grid gap-2.5">
          <div v-for="row in evidence.routes.rows" :key="row.key" class="grid grid-cols-[72px_minmax(0,1fr)_auto] items-center gap-3 text-[14px]">
            <span class="text-gray-600 dark:text-gray-400">{{ row.label }}</span>
            <span class="relative grid h-4 grid-cols-2" aria-hidden="true">
              <span class="absolute inset-y-[-4px] left-1/2 w-px bg-gray-400 dark:bg-gray-500"></span>
              <span class="flex justify-end">
                <span v-if="row.pct < 0" class="block h-full rounded-l-md bg-green-600 dark:bg-green-500" :style="{ width: `${-row.pct / routeMax * 100}%` }"></span>
              </span>
              <span class="flex">
                <span v-if="row.pct > 0" class="block h-full rounded-r-md bg-amber-500 dark:bg-amber-400" :style="{ width: `${row.pct / routeMax * 100}%` }"></span>
              </span>
            </span>
            <b class="min-w-[64px] text-right font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ row.text }}</b>
          </div>
        </div>
        <p class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ evidence.routes.note }}</p>
      </figure>
    </div>
  </ModelBox>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ModelBox from './ModelBox.vue'
import type { EvidenceBar, ModelEvidence } from '../../utils/modelStory'

/** "So messen Fahrer": the figures behind the data sheet. Visible, not folded, for the long-tail search. */
const props = defineProps<{ title: string; evidence: ModelEvidence }>()
const { t } = useI18n()

/** Same colours as the gauge in the hero: year and summer green, winter blue, the prospectus grey */
const BAR_TONE: Record<EvidenceBar['key'], string> = {
  wltp: 'bg-gray-300 dark:bg-gray-600',
  summer: 'bg-green-600 dark:bg-green-500',
  year: 'bg-green-600 dark:bg-green-500',
  winter: 'bg-sky-600 dark:bg-sky-400',
}
const rangeMax = computed(() => Math.max(1, ...(props.evidence.range?.bars ?? []).map(b => b.value)))
/** At least ±10 %, so a 2 % difference does not fill half the track */
const routeMax = computed(() => Math.max(10, ...(props.evidence.routes?.rows ?? []).map(r => Math.abs(r.pct))))
</script>

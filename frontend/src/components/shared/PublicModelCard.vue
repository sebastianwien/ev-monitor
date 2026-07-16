<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BoltIcon,
  BanknotesIcon,
  InformationCircleIcon,
  DocumentCheckIcon,
  ArrowRightIcon,
} from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { TopModelPreview } from '../../api/publicModelService'
import type { ModelImageAttribution } from '../../config/modelImages'

const props = defineProps<{
  model: TopModelPreview
  /** Fully built, locale-aware detail URL (crawlable <a href>). */
  to: string
  /** When set, renders a curated hero image at the top of the card. */
  heroPhotoUrl?: string | null
  /** Attribution for the curated (CC) hero image; rendered as a credit line. */
  heroAttribution?: ModelImageAttribution | null
  /** Search term to highlight in the title (models list only). */
  highlightQuery?: string
  /** Renders a selection ring (e.g. when picked for comparison). */
  selected?: boolean
  /** Normalized EUR/kWh to price this card with; overrides the model's own community price. */
  effectiveCostPerKwh?: number | null
  /** Overrides the cost tooltip text (e.g. the normalized-price explanation). */
  priceTooltip?: string
}>()

const { t } = useI18n()
const { formatConsumption, formatCostPerKwh, formatCostPerDistance, formatNumber, consumptionUnitLabel } = useLocaleFormat()

const heroFailed = ref(false)
const showHero = computed(() => !!props.heroPhotoUrl && !heroFailed.value)

// Price basis: the normalized slider price when provided, else the model's own community price.
const pricePerKwh = computed(() => props.effectiveCostPerKwh ?? props.model.avgCostPerKwh)
// Running cost per 100 km, rounded to the nearest 10 cents for a clean, stable display.
const costPer100km = computed(() => {
  const price = pricePerKwh.value
  const cons = props.model.avgConsumptionKwhPer100km
  return price && cons ? Math.round(price * cons * 10) / 10 : null
})

// Title with optional search-match highlighting. Model names come from our own enum
// (no user input), so v-html here is not an injection surface.
const titleHtml = computed(() => {
  const text = props.model.modelDisplayName
  const q = props.highlightQuery?.trim()
  if (!q) return null
  const terms = q.split(/\s+/).filter(Boolean).map(term => term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  if (terms.length === 0) return null
  const pattern = new RegExp(`(${terms.join('|')})`, 'gi')
  return text.replace(pattern, '<mark class="bg-green-200 dark:bg-green-800 text-green-900 dark:text-green-100 rounded-sm not-italic">$1</mark>')
})

// Unit-less consumption: the unit (kWh/100km) is appended once per value as a
// faint suffix, so the numeric ranges stay short and never wrap.
function formatWltpRangeBare(min: number, max: number | null): string {
  if (!max || Math.abs(max - min) < 0.05) return formatConsumption(min, { showUnit: false })
  return `${formatConsumption(min, { showUnit: false })} - ${formatConsumption(max, { showUnit: false })}`
}
function formatRealConsumptionBare(avg: number | null, min: number | null, max: number | null): string {
  if (min !== null && max !== null) return formatWltpRangeBare(min, max)
  if (avg !== null) return formatConsumption(avg, { showUnit: false })
  return '-'
}

function openAttribution(e: Event) {
  e.preventDefault()
  e.stopPropagation()
  if (props.heroAttribution) window.open(props.heroAttribution.sourceUrl, '_blank', 'noopener')
}
</script>

<template>
  <a
    :href="to"
    class="btn-3d cta-card no-press relative z-0 hover:z-10 flex flex-col bg-white dark:bg-gray-800 border-2 rounded-lg p-4"
    :class="selected ? 'border-green-500 ring-2 ring-green-300 dark:ring-green-800' : 'border-gray-900 dark:border-gray-100'"
  >
    <!-- Hero photo -->
    <div
      v-if="showHero"
      class="relative -mx-4 -mt-4 mb-3 aspect-[16/9] w-[calc(100%+2rem)] overflow-hidden rounded-t-lg bg-gray-100 dark:bg-gray-700"
    >
      <img
        :src="heroPhotoUrl!"
        :alt="model.modelDisplayName"
        loading="lazy"
        decoding="async"
        class="h-full w-full object-cover"
        @error="heroFailed = true"
      />

      <!-- Attribution credit (required for CC-licensed curated images) -->
      <button
        v-if="heroAttribution"
        type="button"
        @click="openAttribution"
        class="absolute bottom-1 left-1 z-10 max-w-[85%] truncate rounded-sm bg-black/50 px-1.5 py-0.5 text-[10px] leading-tight text-white/90 backdrop-blur-sm hover:text-white"
        :title="`${heroAttribution.author} · ${heroAttribution.license} - ${t('models_list.gallery.source')}`"
      >
        © {{ heroAttribution.author }} · {{ heroAttribution.license }}
      </button>
    </div>

    <!-- Optional overlay action (e.g. compare toggle on the models list) -->
    <div v-if="$slots.overlay" class="absolute right-2 top-2 z-20">
      <slot name="overlay" />
    </div>

    <!-- Title -->
    <h3 class="mb-3 text-center text-xl font-extrabold leading-tight text-gray-900 dark:text-gray-100">
      <span v-if="titleHtml" v-html="titleHtml"></span>
      <template v-else>{{ model.modelDisplayName }}</template>
    </h3>

    <!-- Cost hero -->
    <div
      v-if="pricePerKwh && model.avgConsumptionKwhPer100km"
      class="mb-3 rounded-lg border-2 border-gray-900 dark:border-gray-100 bg-blue-50 dark:bg-blue-950/40 px-3 py-2 shadow-[2px_2px_0_0_#bfdbfe]"
    >
      <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">
        <BanknotesIcon class="h-3.5 w-3.5 flex-none" />
        {{ t('landing.hero.cost_headline') }}
      </div>
      <div class="mt-0.5 text-2xl font-bold leading-none text-blue-600 dark:text-blue-400 sl-num">
        ~{{ formatCostPerDistance(costPer100km!) }}
      </div>
      <div class="mt-1 flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <span class="sl-num">{{ t('landing.hero.cost_basis', { price: formatCostPerKwh(pricePerKwh) }) }}</span>
        <span class="relative group cursor-help inline-flex items-center">
          <InformationCircleIcon class="h-3.5 w-3.5 flex-none" />
          <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-sm w-60 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]">
            {{ priceTooltip ?? t('landing.hero.cost_tooltip') }}
          </span>
        </span>
      </div>
    </div>

    <!-- Consumption: real vs factory -->
    <div>
      <div class="text-center text-xs font-bold uppercase tracking-wide text-gray-600 dark:text-gray-300">{{ t('landing.hero.consumption_label') }}</div>
      <div class="mt-1 space-y-0.5 text-sm">
        <div v-if="model.avgConsumptionKwhPer100km || model.minRealConsumptionKwhPer100km" class="flex items-center gap-1.5">
          <BoltIcon class="h-3 w-3 flex-none text-green-600" />
          <span class="text-xs font-semibold text-gray-600 dark:text-gray-400">{{ t('landing.hero.real_short') }}</span>
          <span class="ml-auto whitespace-nowrap font-extrabold text-gray-900 dark:text-gray-100 sl-num">{{ formatRealConsumptionBare(model.avgConsumptionKwhPer100km, model.minRealConsumptionKwhPer100km, model.maxRealConsumptionKwhPer100km) }}<span class="ml-0.5 text-[11px] font-medium text-gray-400">{{ consumptionUnitLabel() }}</span></span>
        </div>
        <div v-if="model.minWltpConsumptionKwhPer100km" class="flex items-center gap-1.5">
          <DocumentCheckIcon class="h-3 w-3 flex-none text-gray-400" />
          <span class="text-xs font-semibold text-gray-500 dark:text-gray-400">{{ t('landing.hero.factory_short') }}</span>
          <span class="ml-auto whitespace-nowrap font-bold text-gray-500 dark:text-gray-400 sl-num">{{ formatWltpRangeBare(model.minWltpConsumptionKwhPer100km, model.maxWltpConsumptionKwhPer100km) }}<span class="ml-0.5 text-[11px] font-medium text-gray-400">{{ consumptionUnitLabel() }}</span></span>
        </div>
      </div>
    </div>

    <!-- Footer: charge count + CTA -->
    <div class="mt-auto flex items-center justify-between gap-2 border-t-2 border-dashed border-gray-200 dark:border-gray-700 pt-2.5">
      <span class="whitespace-nowrap text-xs text-gray-400 sl-num"><span class="font-semibold text-gray-500 dark:text-gray-400">{{ formatNumber(model.logCount) }}</span> {{ t('landing.hero.charging_sessions') }}</span>
      <span class="flex items-center gap-1 whitespace-nowrap text-[13px] font-bold text-green-600 dark:text-green-400">
        {{ t('landing.models_section.view_details') }}
        <ArrowRightIcon class="h-4 w-4 flex-none" />
      </span>
    </div>
  </a>
</template>

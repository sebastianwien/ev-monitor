<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import CostLayers from './CostLayers.vue'
import DataQualityLine from './DataQualityLine.vue'
import StoryLatestLine from './StoryLatestLine.vue'

import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { DayCoverage } from '../../composables/storyModel'
import type { CostLayer } from '../../composables/storyEnergy'
import type { DataQuality, StoryLatest } from '../../composables/storyInsights'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Die Antwort zuerst: Euro je 100 km gross, darunter der Benziner-Vergleich und die
 * Tagesleiste, die zeigt, wo im Monat Daten fehlen. Mit Fixkosten faechert ein Tipp
 * auf die Zahl die Kostenschichten auf. Die Zahl bleibt die eine Zahl des Hero.
 */
const props = defineProps<{
  costPer100km: number | null
  petrolExtraEur: number | null
  petrolPricePerLitre: number
  /** true: Tankerkoenig-Bundesschnitt, false: Rueckfallwert. */
  petrolPriceLive: boolean
  peerDeltaPercent: number | null
  peerCount: number
  monthLabel: string
  days: DayCoverage[]
  layers: CostLayer[] | null
  distanceKm: number | null
  quality: DataQuality | null
  latest: StoryLatest | null
}>()
const emit = defineEmits<{ layers: [open: boolean] }>()

const { t } = useI18n()
const { formatCurrency, formatCostPerDistance, formatDecimal } = useLocaleFormat()

// Count-up nur beim ersten Erscheinen, danach springt die Zahl beim Monatswechsel direkt.
const shown = ref(0)
const counted = ref(false)
const reduceMotion = prefersReducedEffects()

function countTo(target: number) {
  if (reduceMotion || counted.value) { shown.value = target; counted.value = true; return }
  const start = performance.now()
  const duration = 450
  const step = (now: number) => {
    const p = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - p, 3)
    shown.value = target * eased
    if (p < 1) requestAnimationFrame(step)
    else counted.value = true
  }
  requestAnimationFrame(step)
}

onMounted(() => { if (props.costPer100km != null) countTo(props.costPer100km) })
watch(() => props.costPer100km, (v) => { if (v != null) countTo(v) })

// Zahl gross, Einheit kleiner: "7,62 €/100 km" wird zu "7,62" plus "€/100 km".
const bigValue = computed(() => {
  if (props.costPer100km == null) return null
  const s = formatCostPerDistance(shown.value)
  const i = s.indexOf(' ')
  return i < 0 ? { n: s, unit: '' } : { n: s.slice(0, i), unit: s.slice(i + 1) }
})

const layersOpen = ref(false)
function toggleLayers() {
  if (!props.layers) return
  layersOpen.value = !layersOpen.value
  emit('layers', layersOpen.value)
}
// Monatswechsel: Schichten zu, damit die neue Zahl fuer sich steht.
watch(() => props.monthLabel, () => { layersOpen.value = false })

const withData = computed(() => props.days.filter(d => d.hasData).length)
const gap = computed(() => {
  const g = props.days.filter(d => d.inLongestGap)
  return g.length >= 3 ? { from: g[0].day, to: g[g.length - 1].day } : null
})
</script>

<template>
  <!-- view-timeline: die Kopfzeile blendet die Zahl ein, sobald dieser Hero aus dem Bild scrollt (siehe StoryDashboardView). -->
  <section class="story-hero pt-3 pb-5 border-b border-gray-200 dark:border-gray-700" style="view-timeline-name: --story-hero">
    <p class="text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">
      {{ t('story.hero_eyebrow', { month: monthLabel }) }}
    </p>

    <component
      :is="layers ? 'button' : 'p'"
      v-if="bigValue"
      :type="layers ? 'button' : undefined"
      :aria-expanded="layers ? layersOpen : undefined"
      data-testid="story-hero-value"
      class="story-hero-value block text-left font-extrabold tracking-tight leading-none text-gray-900 dark:text-gray-50 tabular-nums mt-1"
      :class="layers ? 'cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent rounded-md' : ''"
      style="view-transition-name: story-hero-value"
      @click="toggleLayers">
      {{ bigValue.n }}<span v-if="bigValue.unit" class="text-[0.38em] font-bold text-gray-500 dark:text-gray-400 ml-2 align-baseline whitespace-nowrap">{{ bigValue.unit }}</span><ChevronDownIcon
        v-if="layers"
        class="inline-block w-6 h-6 ml-1 align-middle text-gray-400 transition-transform duration-300"
        :class="layersOpen ? 'rotate-180' : ''" />
    </component>
    <p v-else class="text-lg mt-2 text-gray-700 dark:text-gray-300 max-w-prose">
      {{ t('story.hero_no_cost') }}
    </p>
    <p v-if="layers && !layersOpen" class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('story.layers_hint') }}</p>
    <CostLayers v-if="layers && layersOpen" :layers="layers" :distance-km="distanceKm" />

    <p v-if="petrolExtraEur != null && petrolExtraEur > 0" class="mt-3 text-[17px] leading-snug text-pretty text-gray-800 dark:text-gray-200 max-w-[34ch]">
      <i18n-t keypath="story.hero_petrol" tag="span">
        <template #eur><b class="text-story-accent dark:text-story-accent-dark">{{ formatCurrency(petrolExtraEur, { decimals: 0 }) }}</b></template>
      </i18n-t>
      <span v-if="peerDeltaPercent != null && peerCount > 0">
        {{ ' ' }}{{ peerDeltaPercent <= 0
          ? t('story.hero_peer_better', { pct: formatDecimal(Math.abs(peerDeltaPercent), 0), count: peerCount })
          : t('story.hero_peer_worse', { pct: formatDecimal(peerDeltaPercent, 0), count: peerCount }) }}
      </span>
    </p>
    <p v-if="petrolExtraEur != null && petrolExtraEur > 0" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
      {{ t(petrolPriceLive ? 'story.hero_petrol_assumption_live' : 'story.hero_petrol_assumption', { price: formatCurrency(petrolPricePerLitre) }) }}
    </p>

    <div v-if="days.length" class="mt-4">
      <svg :viewBox="`0 0 ${days.length * 10.6} 32`" class="block w-full h-8" preserveAspectRatio="none" role="img" :aria-label="t('story.days_aria')">
        <rect
          v-for="d in days" :key="d.day"
          :x="(d.day - 1) * 10.6" :y="d.hasData ? 4 : 20" width="7" :height="d.hasData ? 24 : 8" rx="1.5"
          :class="d.hasData ? 'fill-story-accent dark:fill-story-accent-dark' : d.inLongestGap ? 'fill-amber-500' : 'fill-gray-300 dark:fill-gray-600'"
          :opacity="d.hasData ? 0.85 : 0.7" />
      </svg>
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ t('story.days_caption', { total: days.length, withData }) }}
        <span v-if="gap" class="text-amber-700 dark:text-amber-400">{{ t('story.days_gap', gap) }}</span>
      </p>
      <DataQualityLine v-if="quality" :quality="quality" />
      <StoryLatestLine v-if="latest" :latest="latest" />
    </div>
  </section>
</template>

<style scoped>
.story-hero-value { font-size: clamp(52px, 16vw, 72px); }
</style>

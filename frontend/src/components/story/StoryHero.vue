<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import CostLayers from './CostLayers.vue'
import DataQualityLine from './DataQualityLine.vue'

import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { heroLine, type DayCoverage } from '../../composables/storyModel'
import { keepUnits } from '../../utils/modelStory'
import type { CostLayer } from '../../composables/storyEnergy'
import type { DataQuality } from '../../composables/storyInsights'

/**
 * Die Antwort zuerst, als Satz in Kilometern und Euro: "Oktober: 274 km für 33 €." Darunter
 * grün, was ein Benziner gekostet hätte, klein Euro je 100 km und die Annahme. Die Tagesleiste
 * zeigt, wo im Monat nichts erfasst ist. Mit Fixkosten klappt ein Tipp auf den Satz die
 * Kostenschichten auf.
 */
const props = defineProps<{
  monthLabel: string
  distanceKm: number | null
  /** Stromkosten des Monats, ohne Fixkosten (wie die Kosten-Kachel). */
  energyCostEur: number | null
  charges: number
  costPer100km: number | null
  /** Was ein Benziner für dieselbe Strecke gekostet hätte. */
  petrolEur: number | null
  petrolPricePerLitre: number
  /** true: Tankerkoenig-Bundesschnitt, false: Rueckfallwert. */
  petrolPriceLive: boolean
  days: DayCoverage[]
  layers: CostLayer[] | null
  quality: DataQuality | null
}>()
const emit = defineEmits<{ layers: [open: boolean] }>()

const { t } = useI18n()
const { formatCurrency, formatCostPerDistance, formatDistance } = useLocaleFormat()

const line = computed(() => heroLine(props))
const sentence = computed(() => {
  const l = line.value
  // nb und sv schreiben Monate klein, der Satz beginnt trotzdem groß.
  const month = props.monthLabel.charAt(0).toUpperCase() + props.monthLabel.slice(1)
  const km = l.km != null ? formatDistance(l.km, { round: true }) : ''
  const eur = l.eur != null ? formatCurrency(l.eur, { decimals: 0 }) : ''
  return keepUnits(t(`story.hero_${l.kind}`, { month, km, eur, n: l.charges }, l.charges))
})
// Der Benzin-Satz nur, wenn er etwas zeigt: Strecke und Stromkosten bekannt, Benzin teurer.
const petrol = computed(() => line.value.kind === 'km_cost' && props.petrolEur != null && props.petrolEur > line.value.eur!
  ? formatCurrency(props.petrolEur, { decimals: 0 })
  : null)
const meta = computed(() => [
  // Je 100 km nur, wenn der Satz km und Euro nennt: ohne Preise wäre es 0,00.
  props.costPer100km != null && line.value.kind === 'km_cost' ? formatCostPerDistance(props.costPer100km) : null,
  petrol.value ? t(props.petrolPriceLive ? 'story.hero_petrol_assumption_live' : 'story.hero_petrol_assumption', { price: formatCurrency(props.petrolPricePerLitre) }) : null,
].filter(Boolean).join(' · '))

const layersOpen = ref(false)
function toggleLayers() {
  if (!props.layers) return
  layersOpen.value = !layersOpen.value
  emit('layers', layersOpen.value)
}
// Monatswechsel: Schichten zu, damit der neue Satz für sich steht.
watch(() => props.monthLabel, () => { layersOpen.value = false })

const withData = computed(() => props.days.filter(d => d.hasData).length)
const gap = computed(() => {
  const g = props.days.filter(d => d.inLongestGap)
  return g.length >= 3 ? { from: g[0].day, to: g[g.length - 1].day } : null
})
</script>

<template>
  <div class="story-hero grid gap-2">
    <component
      :is="layers ? 'button' : 'p'"
      :type="layers ? 'button' : undefined"
      :aria-expanded="layers ? layersOpen : undefined"
      data-testid="story-hero-value"
      class="block text-left text-pretty text-[30px] font-extrabold leading-[1.1] tracking-tight text-gray-900 tabular-nums md:text-[44px] dark:text-gray-50"
      :class="layers ? 'cursor-pointer rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent' : ''"
      style="view-transition-name: story-hero-value"
      @click="toggleLayers">
      {{ sentence }}<ChevronDownIcon
        v-if="layers"
        class="ml-1 inline-block h-6 w-6 align-middle text-gray-400 transition-transform duration-300"
        :class="layersOpen ? 'rotate-180' : ''" />
    </component>
    <p v-if="petrol" data-testid="story-hero-petrol" class="text-pretty text-[30px] font-extrabold leading-[1.1] tracking-tight text-story-accent tabular-nums md:text-[44px] dark:text-story-accent-dark">
      {{ t('story.hero_petrol_total', { eur: petrol }) }}
    </p>
    <p v-if="meta || layers" data-testid="story-hero-meta" class="text-[13px] text-pretty text-gray-500 dark:text-gray-400">
      {{ meta }}<template v-if="layers && !layersOpen">{{ meta ? ' · ' : '' }}{{ t('story.layers_hint') }}</template>
    </p>
    <CostLayers v-if="layers && layersOpen" :layers="layers" :distance-km="distanceKm" />

    <div v-if="days.length" class="mt-2">
      <svg :viewBox="`0 0 ${days.length * 10.6} 32`" class="block h-8 w-full" preserveAspectRatio="none" role="img" :aria-label="t('story.days_aria')">
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
    </div>
  </div>
</template>

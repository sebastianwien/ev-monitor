<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShareIcon } from '@heroicons/vue/24/outline'
import CostLayers from './CostLayers.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { heroLine } from '../../composables/storyModel'
import { keepUnits } from '../../utils/modelStory'
import type { CostLayer } from '../../composables/storyEnergy'

/**
 * Die Antwort zuerst, als ein Satz in Kilometern und Euro: "Oktober: 274 km für 33 €."
 * Darunter eine graue Zeile mit Euro je 100 km und, nur wenn Benzin teurer gewesen wäre, was
 * ein Benziner gekostet hätte. Rechts der Teilen-Knopf. Mit Fixkosten klappt ein Tipp auf den
 * Satz die Kostenschichten auf.
 */
const props = defineProps<{
  monthLabel: string
  distanceKm: number | null
  /** Stromkosten des Monats, ohne Fixkosten (wie die Kosten-Kachel). */
  energyCostEur: number | null
  charges: number
  /** Was ein Benziner für dieselbe Strecke gekostet hätte. */
  petrolEur: number | null
  layers: CostLayer[] | null
}>()
const emit = defineEmits<{ layers: [open: boolean]; share: [text: string] }>()

const { t } = useI18n()
const { formatCurrency, formatDistance, convertDistance, distanceUnitLabel } = useLocaleFormat()

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
/** Monat und Rest getrennt: umbrechen darf der Satz nur nach "Oktober:". */
const sentenceParts = computed(() => {
  const i = sentence.value.indexOf(': ')
  return i < 0 ? { month: '', rest: sentence.value } : { month: sentence.value.slice(0, i + 1), rest: sentence.value.slice(i + 2) }
})
const meta = computed(() => [
  // Je 100 km aus dem Satz selbst, damit Nachrechnen aufgeht; nur wenn er km und Euro nennt.
  // In der Strecken-Einheit des Satzes (km oder mi), damit beide zusammenpassen.
  line.value.kind === 'km_cost'
    ? t('story.hero_per_100', { eur: formatCurrency(line.value.eur! / convertDistance(line.value.km!) * 100), unit: distanceUnitLabel() })
    : null,
  petrol.value ? t('story.hero_petrol_sub', { eur: petrol.value }) : null,
].filter(Boolean).map(x => keepUnits(x!)).join(' · '))

const layersOpen = ref(false)
function toggleLayers() {
  if (!props.layers) return
  layersOpen.value = !layersOpen.value
  emit('layers', layersOpen.value)
}
// Monatswechsel: Schichten zu, damit der neue Satz für sich steht.
watch(() => props.monthLabel, () => { layersOpen.value = false })
</script>

<template>
  <div class="story-hero flex items-start justify-between gap-3">
    <div class="grid min-w-0 gap-1.5">
      <h1 class="text-pretty text-[26px] font-bold leading-[1.1] tracking-tight text-gray-900 tabular-nums md:text-[36px] dark:text-gray-50">
        <component
          :is="layers ? 'button' : 'span'"
          :type="layers ? 'button' : undefined"
          :aria-expanded="layers ? layersOpen : undefined"
          data-testid="story-hero-value"
          class="block text-left"
          :class="layers ? 'cursor-pointer rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent' : ''"
          style="view-transition-name: story-hero-value"
          @click="toggleLayers">
          <template v-if="sentenceParts.month">{{ `${sentenceParts.month} ` }}</template><span class="whitespace-nowrap">{{ sentenceParts.rest }}</span>
        </component>
      </h1>
      <p v-if="meta || layers" data-testid="story-hero-meta" class="text-pretty text-[14px] text-gray-500 tabular-nums dark:text-gray-400">
        {{ meta }}<template v-if="layers && !layersOpen">{{ meta ? ' · ' : '' }}{{ t('story.layers_hint') }}</template>
      </p>
      <CostLayers v-if="layers && layersOpen" :layers="layers" :distance-km="distanceKm" />
    </div>
    <button
      type="button"
      data-testid="story-hero-share"
      class="-mr-2 -mt-1 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
      :aria-label="t('story.share_aria')"
      @click="emit('share', [sentence, meta].filter(Boolean).join(' '))">
      <ShareIcon class="h-6 w-6" aria-hidden="true" />
    </button>
  </div>
</template>

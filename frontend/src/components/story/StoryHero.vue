<script setup lang="ts">
import { ref, watch, computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShareIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import CostLayers from './CostLayers.vue'
import Collapse from '../shared/Collapse.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { heroLine } from '../../composables/storyModel'
import { keepUnits } from '../../utils/modelStory'
import type { CostLayer } from '../../composables/storyEnergy'

/**
 * Die Antwort zuerst, als ein Satz in Kilometern und Euro: "Oktober: 274 km für 33 €."
 * Darunter eine graue Zeile mit Euro je 100 km und, nur wenn Benzin teurer gewesen wäre, was
 * ein Benziner gekostet hätte. Rechts der Teilen-Knopf. Mit Fixkosten oder Einnahmen steht in der
 * grauen Zeile ein Knopf "Alle Kosten" mit der Summe, er klappt die Kostenschichten auf.
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

/** Summe aller Schichten (Strom plus Fixkosten minus Einnahmen) für den Knopf. */
const layersTotal = computed(() => {
  const net = props.layers?.find(l => l.kind === 'net')
  return net ? keepUnits(t('story.layers_toggle', { eur: formatCurrency(net.eur, { decimals: 0 }) })) : null
})
const layersId = useId()
const layersOpen = ref(false)
function toggleLayers() {
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
        <span data-testid="story-hero-value" class="block">
          <template v-if="sentenceParts.month">{{ `${sentenceParts.month} ` }}</template><span class="whitespace-nowrap">{{ sentenceParts.rest }}</span>
        </span>
      </h1>
      <!-- Passt der Knopf nicht mehr hinter die graue Zeile, bricht er in eine eigene Zeile um.
           -my gleicht die 44 px Tap-Fläche aus, damit die Zeile nicht höher wird als der Text. -->
      <div v-if="meta || layersTotal" class="flex flex-wrap items-center gap-x-3 gap-y-1">
        <p v-if="meta" data-testid="story-hero-meta" class="text-pretty text-[15px] font-semibold text-gray-600 tabular-nums md:text-[17px] dark:text-gray-300">{{ meta }}</p>
        <button
          v-if="layersTotal"
          type="button"
          data-testid="story-hero-layers"
          :aria-expanded="layersOpen"
          :aria-controls="layersId"
          class="-my-2.5 inline-flex min-h-11 items-center gap-1 rounded-md text-[14px] font-bold text-gray-900 tabular-nums underline decoration-gray-300 underline-offset-4 hover:decoration-gray-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 md:text-[15px] dark:text-gray-50 dark:decoration-gray-600 dark:focus-visible:ring-gray-100"
          @click="toggleLayers">
          {{ layersTotal }}
          <ChevronDownIcon class="h-4 w-4 shrink-0 transition-transform duration-300" :class="layersOpen ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
        </button>
      </div>
      <Collapse v-if="layers" :open="layersOpen">
        <div :id="layersId"><CostLayers :layers="layers" /></div>
      </Collapse>
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

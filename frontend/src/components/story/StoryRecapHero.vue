<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrophyIcon, BoltIcon, BanknotesIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import type { Moment } from '../../composables/storyMoments'
import { useMomentView } from '../../composables/useMomentView'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { keepUnits } from '../../utils/modelStory'
import BoldNumbers from './BoldNumbers.vue'

/**
 * Monatsrückblick als Kopf der Seite, solange der laufende Monat leer ist: Satz und Ersparnis wie die
 * Karte, darunter Kosten pro 100 km, Energie und Ladungen, der Ausblick auf den laufenden Monat (Slot),
 * das gesparte CO₂, Platz und Modellvergleich. Die Ersparnis steht nur im Satz, nicht noch einmal als
 * Zahl; Normal- gegen Schnellladen steht in der Strom-Kachel des Vormonats. Die ganze Karte öffnet den Vormonat.
 */
const props = defineProps<{ moment: Moment; carId: string | null; carName: string }>()
const emit = defineEmits<{ act: [id: string] }>()
const { t } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()
const view = useMomentView(props)

const recap = computed(() => props.moment.kind === 'month_change' ? props.moment : null)
const facts = computed(() => recap.value?.facts ?? null)

const kpis = computed(() => {
  const m = recap.value
  if (!m) return []
  const per100 = m.km != null && m.km > 0 && m.eur != null ? (m.eur / m.km) * 100 : null
  return [
    per100 != null ? { id: 'per100', value: formatCurrency(per100, { decimals: 2 }), label: t('story.recap_eur_100km') } : null,
    facts.value ? { id: 'kwh', value: `${formatDecimal(facts.value.kwh, 0)} kWh`, label: t('story.recap_kwh') } : null,
    { id: 'charges', value: String(m.charges), label: t('story.recap_charges', m.charges) },
  ].filter((x): x is { id: string; value: string; label: string } => x != null)
})

const co2 = computed(() => facts.value?.co2Kg != null ? t('story.recap_co2', { kg: `${formatDecimal(facts.value.co2Kg, 0)} kg` }) : null)

/** Die ganze Karte führt in den Vormonat, wie der Pfeil im Monatswähler. */
function open() {
  if (view.value.action) emit('act', view.value.action.id)
}
</script>

<template>
  <!-- Tippen auf die Karte öffnet den Vormonat; Tastatur und Screenreader nutzen den eigenen Knopf -->
  <article class="grid cursor-pointer gap-4" data-testid="story-recap-hero" :aria-label="view.eyebrow" @click="open">
    <button v-if="view.action" type="button" class="sr-only focus:not-sr-only focus:justify-self-start focus:text-[15px] focus:font-semibold focus:text-green-700 dark:focus:text-green-400" @click.stop="open">{{ view.action.label }}</button>
    <div class="grid gap-1">
      <div class="flex items-center justify-between gap-2">
        <p class="text-[16px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ view.eyebrow }}</p>
        <!-- Monatswähler; Klicks dort öffnen nicht die Karte -->
        <div @click.stop><slot /></div>
      </div>
      <p class="text-pretty text-[24px] font-bold leading-[1.15] tracking-tight text-gray-900 tabular-nums md:text-[28px] dark:text-gray-50">{{ keepUnits(view.sentence) }}</p>
      <p v-if="view.sub" class="text-pretty text-[15px] text-gray-700 tabular-nums dark:text-gray-200"><BoldNumbers :text="keepUnits(view.sub)" /></p>
    </div>

    <ul class="grid divide-x divide-gray-200 rounded-xl bg-white py-3 edge-card dark:divide-white/15 dark:bg-gray-700/50" :class="['grid-cols-1', 'grid-cols-2', 'grid-cols-3'][kpis.length - 1]">
      <li v-for="k in kpis" :key="k.id" data-testid="recap-fact" class="grid gap-0.5 px-2 text-center">
        <strong class="whitespace-nowrap text-[20px] font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ keepUnits(k.value) }}</strong>
        <span class="text-[12px] text-gray-600 dark:text-gray-300">{{ k.label }}</span>
      </li>
    </ul>

    <slot name="outlook" />

    <ul v-if="co2 || view.details?.length" class="grid gap-1.5">
      <li v-if="co2" data-testid="recap-co2" class="flex items-start gap-2 text-pretty text-[15px] text-gray-900 tabular-nums dark:text-gray-50">
        <SparklesIcon class="mt-0.5 h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <BoldNumbers :text="co2" />
      </li>
      <li v-for="d in view.details" :key="d.id" class="flex items-start gap-2 text-pretty text-[15px] text-gray-900 tabular-nums dark:text-gray-50">
        <component :is="d.id === 'rank' ? TrophyIcon : d.id === 'cost' ? BanknotesIcon : BoltIcon" class="mt-0.5 h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <BoldNumbers :text="keepUnits(d.text)" />
      </li>
    </ul>

  </article>
</template>

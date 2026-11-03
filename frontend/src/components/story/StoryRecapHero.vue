<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrophyIcon, BoltIcon, BanknotesIcon } from '@heroicons/vue/24/outline'
import type { Moment } from '../../composables/storyMoments'
import { useMomentView } from '../../composables/useMomentView'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { keepUnits } from '../../utils/modelStory'
import BoldNumbers from './BoldNumbers.vue'

/**
 * Monatsrückblick als Kopf der Seite, solange der laufende Monat leer ist: der Satz wie der Monats-Hero,
 * darunter die Ersparnis gegenüber dem Verbrenner als die eine grüne Fläche, dann Kosten pro 100 km,
 * Energie und Ladungen als Kachelzahlen ohne eigene Box, der Ausblick auf den laufenden Monat (Slot),
 * Platz und Modellvergleich. Normal- gegen Schnellladen steht in der Strom-Kachel des Vormonats.
 * Die ganze Karte öffnet den Vormonat.
 */
/** secondary: steht unter dem Monatskopf statt als Seitenkopf, dann tritt der Satz eine Stufe zurück */
const props = defineProps<{ moment: Moment; carId: string | null; carName: string; secondary?: boolean }>()
const emit = defineEmits<{ act: [id: string] }>()
const { t } = useI18n()
const { formatDecimal, currencyParts } = useLocaleFormat()
const view = useMomentView(props)

const recap = computed(() => props.moment.kind === 'month_change' ? props.moment : null)
const facts = computed(() => recap.value?.facts ?? null)

const kpis = computed(() => {
  const m = recap.value
  if (!m) return []
  const per100 = m.km != null && m.km > 0 && m.eur != null ? (m.eur / m.km) * 100 : null
  return [
    per100 != null ? { id: 'per100', ...currencyParts(per100, { decimals: 2 }), label: t('story.recap_eur_100km') } : null,
    facts.value ? { id: 'kwh', n: formatDecimal(facts.value.kwh, 0), unit: 'kWh', label: t('story.recap_kwh') } : null,
    { id: 'charges', n: String(m.charges), unit: '', label: t('story.recap_charges', m.charges) },
  ].filter((x): x is { id: string; n: string; unit: string; label: string } => x != null)
})

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
      <p class="text-pretty font-bold leading-[1.1] tracking-tight text-gray-900 tabular-nums dark:text-gray-50" :class="secondary ? 'text-[20px] md:text-[26px]' : 'text-[26px] md:text-[36px]'">{{ keepUnits(view.sentence) }}</p>
    </div>

    <!-- Die gute Nachricht des Monats: die eine grüne Fläche der Seite -->
    <p v-if="view.sub" data-testid="recap-saving" class="text-pretty rounded-xl bg-green-50 px-4 py-3 text-[17px] leading-snug text-gray-900 tabular-nums edge-card dark:bg-green-900/20 dark:text-gray-50"><BoldNumbers :text="keepUnits(view.sub)" units /><template v-if="view.cheer">{{ ' ' }}<strong class="font-bold">{{ view.cheer }}</strong></template></p>

    <ul class="grid divide-x divide-gray-200 dark:divide-white/15" :class="['grid-cols-1', 'grid-cols-2', 'grid-cols-3'][kpis.length - 1]">
      <li v-for="k in kpis" :key="k.id" data-testid="recap-fact" class="grid content-start gap-1 px-1 text-center">
        <span class="flex flex-wrap items-baseline justify-center gap-x-1 tabular-nums"><b class="text-[26px] font-extrabold leading-none tracking-tight text-gray-900 dark:text-gray-50">{{ k.n }}</b> <span v-if="k.unit" class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ k.unit }}</span></span>
        <span class="text-[11.5px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ k.label }}</span>
      </li>
    </ul>

    <div v-if="$slots.outlook" class="border-t border-gray-200 pt-4 dark:border-white/15"><slot name="outlook" /></div>

    <ul v-if="view.details?.length" class="grid gap-1.5">
      <li v-for="d in view.details" :key="d.id" class="flex items-start gap-2 text-pretty text-[15px] text-gray-900 tabular-nums dark:text-gray-50">
        <component :is="d.id === 'rank' ? TrophyIcon : d.id === 'cost' ? BanknotesIcon : BoltIcon" class="mt-0.5 h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <BoldNumbers :text="keepUnits(d.text)" />
      </li>
    </ul>

  </article>
</template>

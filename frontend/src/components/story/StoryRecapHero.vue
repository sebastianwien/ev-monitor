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
 * Karte, darunter Strecke, Energie und Ladungen, Normal- gegen Schnellladen mit Kosten, das gesparte
 * CO₂, Platz und Modellvergleich. Die ganze Karte öffnet den Vormonat.
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
    m.savingEur != null ? { id: 'saving', value: formatCurrency(m.savingEur, { decimals: 0 }), label: t('story.recap_saving') } : null,
    per100 != null ? { id: 'per100', value: formatCurrency(per100, { decimals: 2 }), label: t('story.recap_eur_100km') } : null,
    facts.value ? { id: 'kwh', value: `${formatDecimal(facts.value.kwh, 0)} kWh`, label: t('story.recap_kwh') } : null,
    { id: 'charges', value: String(m.charges), label: t('story.recap_charges', m.charges) },
  ].filter((x): x is { id: string; value: string; label: string } => x != null)
})

const split = computed(() => {
  const f = facts.value
  if (!f) return []
  const total = (f.ac?.kwh ?? 0) + (f.dc?.kwh ?? 0)
  return [
    f.ac ? { id: 'ac', label: t('story.charging_ac'), ...f.ac, bar: 'bg-green-600 dark:bg-green-500', cls: 'text-green-700 dark:text-green-400' } : null,
    f.dc ? { id: 'dc', label: t('story.charging_dc'), ...f.dc, bar: 'bg-sky-600 dark:bg-sky-500', cls: 'text-sky-700 dark:text-sky-400' } : null,
  ].filter(x => x != null).map(x => ({ ...x!, share: total > 0 ? (x!.kwh / total) * 100 : 0 }))
})

const co2 = computed(() => facts.value?.co2Kg != null ? t('story.recap_co2', { kg: `${formatDecimal(facts.value.co2Kg, 0)} kg` }) : null)

/** Die ganze Karte führt in den Vormonat, wie der Pfeil im Monatswähler. */
function open() {
  if (view.value.action) emit('act', view.value.action.id)
}
</script>

<template>
  <article class="grid cursor-pointer gap-4 rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-green-600" data-testid="story-recap-hero"
    :aria-label="view.action?.label ?? view.eyebrow" :role="view.action ? 'button' : undefined" :tabindex="view.action ? 0 : undefined"
    @click="open" @keydown.enter.prevent="open" @keydown.space.prevent="open">
    <div class="grid gap-1">
      <div class="flex items-center justify-between gap-2">
        <p class="text-[16px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ view.eyebrow }}</p>
        <!-- Monatswähler; Klicks dort öffnen nicht die Karte -->
        <div @click.stop @keydown.stop><slot /></div>
      </div>
      <p class="text-pretty text-[24px] font-bold leading-[1.15] tracking-tight text-gray-900 tabular-nums md:text-[28px] dark:text-gray-50">{{ keepUnits(view.sentence) }}</p>
      <p v-if="view.sub" class="text-pretty text-[15px] text-gray-700 tabular-nums dark:text-gray-200"><BoldNumbers :text="keepUnits(view.sub)" /></p>
    </div>

    <ul class="grid divide-x divide-gray-200 rounded-xl bg-white py-3 edge-card dark:divide-white/15 dark:bg-gray-700/50" :class="['grid-cols-1', 'grid-cols-2', 'grid-cols-3', 'grid-cols-4'][kpis.length - 1]">
      <li v-for="k in kpis" :key="k.id" data-testid="recap-fact" class="grid gap-0.5 px-2 text-center">
        <strong class="whitespace-nowrap text-[20px] font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ keepUnits(k.value) }}</strong>
        <span class="text-[12px] text-gray-600 dark:text-gray-300">{{ k.label }}</span>
      </li>
    </ul>

    <section v-if="split.length" class="grid gap-2" :aria-label="t('story.recap_split')">
      <div class="flex h-2.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true">
        <span v-for="s in split" :key="s.id" :class="s.bar" :style="{ width: `${s.share}%` }" />
      </div>
      <ul class="grid gap-1 text-[15px] tabular-nums">
        <li v-for="s in split" :key="s.id" data-testid="recap-split" class="flex items-baseline gap-3">
          <span class="flex-1 font-semibold" :class="s.cls">{{ s.label }}</span>
          <strong v-if="s.eur != null" class="font-bold text-gray-900 dark:text-gray-50">{{ formatCurrency(s.eur, { decimals: 0 }) }}</strong>
          <span class="w-16 text-right text-[13px] text-gray-600 dark:text-gray-300">{{ formatDecimal(s.kwh, 0) }} kWh</span>
        </li>
      </ul>
    </section>

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

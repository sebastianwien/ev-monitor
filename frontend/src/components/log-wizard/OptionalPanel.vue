<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { Component } from 'vue'
import { ROUTE_CHIPS, TIRE_CHIPS } from './optionalChips'
import type { LogFormData } from '../log-form/logFormData'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { optionalFacts } from './wizardLogic'
import OptionalDetails from './OptionalDetails.vue'

/**
 * "Mehr Details" zum Aufklappen. Zugeklappt zeigt eine Zeile kleiner Pillen nur die Werte,
 * die gerade gesetzt sind - als Schnellkontrolle, ohne das Formular zu öffnen.
 * Steht in Schritt 2 unter dem Preis und in der Zusammenfassung (Bearbeiten).
 */
// Boolean-Props ohne Angabe werden false - die Zeit soll aber standardmäßig dabei sein
const props = withDefaults(defineProps<{ showTime?: boolean; numbersOnly?: boolean }>(), { showTime: true, numbersOnly: false })
const form = defineModel<LogFormData>({ required: true })
const { t, locale } = useI18n()
const { formatNumber } = useLocaleFormat()

const timeLabel = computed(() => {
  if (!form.value.loggedAt) return t('logwizard.time_now')
  return new Date(form.value.loggedAt).toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
})
interface Pill { text: string; icon?: Component; empty?: boolean }
/** Nur Zahlen (Schritt 3): alle drei als Pille, ungesetzte gestrichelt mit ihrem Namen - so sieht man, was noch fehlt */
const numberPills = computed<Pill[]>(() => [
  { key: 'd_soc_before_tile', v: form.value.socBeforeChargePercent, unit: '%' },
  { key: 'd_duration', v: form.value.chargeDurationMinutes, unit: 'min' },
  { key: 'd_peak_tile', v: form.value.maxChargingPowerKw, unit: 'kW' },
].map(x => x.v != null && x.v > 0 ? { text: `${formatNumber(x.v)} ${x.unit}` } : { text: t(`logwizard.${x.key}`), empty: true }))
const facts = computed<Pill[]>(() => props.numbersOnly ? numberPills.value : optionalFacts(form.value, { withTime: props.showTime }).map(f => {
  switch (f.kind) {
    case 'time': return { text: timeLabel.value }
    case 'socBefore': return { text: `${f.value} % ${t('logwizard.d_soc_before_short')}` }
    case 'route': { const c = ROUTE_CHIPS.find(c => c.value === f.value)!; return { text: t(c.key), icon: c.icon } }
    case 'tires': { const c = TIRE_CHIPS.find(c => c.value === f.value)!; return { text: t(c.key), icon: c.icon } }
    case 'duration': return { text: `${f.value} min` }
    case 'peak': return { text: `${formatNumber(f.value as number)} kW` }
  }
}))
const details = ref<HTMLDetailsElement | null>(null)
const open = ref(false)
</script>

<template>
  <details ref="details" data-testid="optional-panel" class="group" @toggle="open = details?.open ?? false">
    <!-- Eine Zeile: Titel links, zugeklappt rechts die gesetzten Werte als Pillen mit Icon; was nicht passt, läuft weich aus -->
    <summary class="flex items-center gap-2 min-h-11 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
      <span class="flex items-center gap-1 flex-shrink-0 text-sm font-semibold text-gray-800 dark:text-gray-100">
        <ChevronRightIcon class="h-4 w-4 text-gray-400 transition group-open:rotate-90" />{{ t('logwizard.more_details') }}
      </span>
      <span v-if="open" class="text-xs text-gray-400">· {{ t('logfields.optional') }}</span>
      <span v-else-if="facts.length" data-testid="summary-optional" class="flex flex-1 min-w-0 gap-1.5 overflow-hidden [mask-image:linear-gradient(to_right,black_88%,transparent)]">
        <span v-for="f in facts" :key="f.text" :class="['inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-1 text-[11px] tabular-nums',
            f.empty ? 'border border-dashed border-gray-300 dark:border-gray-600 text-gray-400 dark:text-gray-500' : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300']">
          <component :is="f.icon" v-if="f.icon" class="h-3.5 w-3.5 text-gray-500 dark:text-gray-400" />{{ f.text }}
        </span>
      </span>
    </summary>
    <OptionalDetails v-model="form" :show-time="showTime" />
  </details>
</template>

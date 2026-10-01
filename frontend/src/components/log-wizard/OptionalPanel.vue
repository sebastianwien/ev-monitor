<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
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
const props = withDefaults(defineProps<{ showTime?: boolean }>(), { showTime: true })
const form = defineModel<LogFormData>({ required: true })
const { t, locale } = useI18n()
const { formatNumber } = useLocaleFormat()

const timeLabel = computed(() => {
  if (!form.value.loggedAt) return t('logwizard.time_now')
  return new Date(form.value.loggedAt).toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
})
const routeLabel: Record<LogFormData['routeType'], string> = { CITY: 'd_route_city', COMBINED: 'd_route_mixed', HIGHWAY: 'd_route_highway' }
const tireLabel: Record<LogFormData['tireType'], string> = { SUMMER: 'd_tire_summer', ALL_YEAR: 'd_tire_allyear', WINTER: 'd_tire_winter' }
const facts = computed(() => optionalFacts(form.value, { withTime: props.showTime }).map(f => {
  switch (f.kind) {
    case 'time': return timeLabel.value
    case 'socBefore': return `${f.value} % ${t('logwizard.d_soc_before_short')}`
    case 'route': return t(`logwizard.${routeLabel[f.value as LogFormData['routeType']]}`)
    case 'tires': return t(`logwizard.${tireLabel[f.value as LogFormData['tireType']]}`)
    case 'duration': return `${f.value} min`
    case 'peak': return `${formatNumber(f.value as number)} kW`
  }
}))
const details = ref<HTMLDetailsElement | null>(null)
const open = ref(false)
</script>

<template>
  <details ref="details" data-testid="optional-panel" class="group" @toggle="open = details?.open ?? false">
    <summary class="py-2 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
      <span class="flex items-center gap-1.5 text-sm font-semibold text-gray-800 dark:text-gray-100">
        <ChevronRightIcon class="h-4 w-4 text-gray-400 transition group-open:rotate-90" />
        {{ t('logwizard.more_details') }} <span class="font-normal text-gray-400">· {{ t('logfields.optional') }}</span>
      </span>
      <!-- Zugeklappt: die gesetzten Werte als Pillen, eine Zeile, Rest läuft weich aus -->
      <span v-if="!open && facts.length" data-testid="summary-optional" class="mt-1 flex gap-1.5 overflow-hidden pl-[1.375rem] [mask-image:linear-gradient(to_right,black_85%,transparent)]">
        <span v-for="f in facts" :key="f" class="whitespace-nowrap rounded-full bg-gray-100 dark:bg-gray-700 px-2 py-0.5 text-[11px] tabular-nums text-gray-600 dark:text-gray-300">{{ f }}</span>
      </span>
    </summary>
    <OptionalDetails v-model="form" :show-time="showTime" />
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { RequiredField } from './wizardLogic'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import OptionalDetails from './OptionalDetails.vue'

export type SummarySection = 'place' | 'energy' | 'vehicle' | 'cost' | 'time'

const props = defineProps<{
  placeLabel: string
  /** Pflichtwerte, die noch fehlen - ihre Kacheln zeigen "offen" statt eines Werts */
  missing?: RequiredField[]
  /** Zeit als eigene Kachel (Bearbeiten); beim Anlegen steht sie unter "Mehr Details" */
  showTimeTile?: boolean
  detailsOpen?: boolean
}>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ edit: [section: SummarySection] }>()
const { t, locale } = useI18n()
const { formatDistance, formatCurrency, formatNumber } = useLocaleFormat()

const timeLabel = computed(() => {
  if (!form.value.loggedAt) return t('logwizard.time_now')
  return new Date(form.value.loggedAt).toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
})

const isMissing = (f: RequiredField) => props.missing?.includes(f) ?? false
const energy = computed(() => {
  const v = form.value.kwhCharged ?? form.value.kwhAtVehicle
  return v == null ? null : `${formatNumber(v)} kWh`
})
interface Tile { label: string; value: string | null; section: SummarySection; testid: string }
const tiles = computed<Tile[]>(() => [
  { label: t('logwizard.place'), value: props.placeLabel, section: 'place', testid: 'summary-place' },
  ...(props.showTimeTile ? [{ label: t('logfields.timestamp'), value: timeLabel.value, section: 'time' as SummarySection, testid: 'summary-time' }] : []),
  { label: t('logfields.energy'), value: isMissing('energy') ? null : energy.value, section: 'energy', testid: 'summary-energy' },
  { label: t('logfields.odometer'), value: isMissing('odometer') || form.value.odometerKm == null ? null : formatDistance(form.value.odometerKm), section: 'vehicle', testid: 'summary-odometer' },
  { label: t('logfields.soc_after'), value: isMissing('soc') || form.value.socAfterChargePercent == null ? null : `${form.value.socAfterChargePercent} %`, section: 'vehicle', testid: 'summary-soc' },
  { label: t('logfields.cost_eur'), value: isMissing('cost') || form.value.costEur == null ? null : formatCurrency(form.value.costEur), section: 'cost', testid: 'summary-cost' },
])
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 gap-2">
      <button v-for="tile in tiles" :key="tile.testid" type="button" :data-testid="tile.testid" @click="emit('edit', tile.section)"
        :class="['btn-3d text-left p-3 rounded-sm transition', tile.value == null ? 'bg-amber-50 dark:bg-amber-900/20 ring-1 ring-inset ring-amber-300 dark:ring-amber-700 hover:bg-amber-100 dark:hover:bg-amber-900/40' : 'bg-gray-100 dark:bg-gray-700/60 hover:bg-gray-200 dark:hover:bg-gray-700']">
        <span class="block text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ tile.label }}</span>
        <b v-if="tile.value != null" class="block text-base font-semibold tabular-nums text-gray-800 dark:text-gray-100 truncate">{{ tile.value }}</b>
        <b v-else class="block text-base font-semibold text-amber-700 dark:text-amber-300">{{ t('logwizard.open') }}</b>
        <span class="text-xs text-indigo-600 dark:text-indigo-300">{{ t('logwizard.change') }}</span>
      </button>
    </div>

    <details :open="detailsOpen" class="group">
      <summary class="py-2 text-sm font-semibold cursor-pointer list-none flex items-center gap-1.5 text-gray-800 dark:text-gray-100">
        <ChevronRightIcon class="h-4 w-4 text-gray-400 transition group-open:rotate-90" />
        {{ t('logwizard.more_details') }} <span class="font-normal text-gray-400">· {{ t('logfields.optional') }}</span>
      </summary>
      <OptionalDetails v-model="form" :show-time="!showTimeTile" />
    </details>
  </div>
</template>

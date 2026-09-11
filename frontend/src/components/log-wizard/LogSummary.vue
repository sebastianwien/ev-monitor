<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/LogFormFields.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { RequiredField } from './wizardLogic'
import SegmentToggle from './SegmentToggle.vue'

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

const pad = (n: number) => String(n).padStart(2, '0')
const nowLocal = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}` }
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
        :class="['text-left p-3 rounded-sm', tile.value == null ? 'bg-amber-50 dark:bg-amber-900/20 ring-1 ring-inset ring-amber-300 dark:ring-amber-700' : 'bg-gray-100 dark:bg-gray-700/60']">
        <span class="block text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ tile.label }}</span>
        <b v-if="tile.value != null" class="block text-base font-semibold tabular-nums text-gray-800 dark:text-gray-100 truncate">{{ tile.value }}</b>
        <b v-else class="block text-base font-semibold text-amber-700 dark:text-amber-300">{{ t('logwizard.open') }}</b>
        <span class="text-xs text-indigo-600 dark:text-indigo-300">{{ t('logwizard.change') }}</span>
      </button>
    </div>

    <details :open="detailsOpen" class="rounded-sm border border-gray-200 dark:border-gray-700">
      <summary class="px-3 py-2.5 text-sm font-semibold cursor-pointer text-gray-800 dark:text-gray-100">
        {{ t('logwizard.more_details') }} <span class="font-normal text-gray-400">· {{ t('logfields.optional') }}</span>
      </summary>
      <div class="px-3 pb-3 space-y-4">
        <div v-if="!showTimeTile">
          <label for="wizard-time" class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.timestamp') }}</label>
          <input id="wizard-time" v-model="form.loggedAt" type="datetime-local" :max="nowLocal()"
            class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 p-2 text-sm" />
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.timestamp_hint') }}</p>
        </div>
        <div>
          <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.route_type_label') }}</label>
          <SegmentToggle v-model="form.routeType"
            :options="[{ value: 'CITY', label: t('logfields.route_city') }, { value: 'COMBINED', label: t('logfields.route_mix') }, { value: 'HIGHWAY', label: t('logfields.route_highway') }]" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.tire_type_label') }}</label>
          <SegmentToggle v-model="form.tireType"
            :options="[{ value: 'SUMMER', label: t('logfields.tire_summer') }, { value: 'ALL_YEAR', label: t('logfields.tire_allyear') }, { value: 'WINTER', label: t('logfields.tire_winter') }]" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label for="wizard-duration" class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.duration') }}</label>
            <input id="wizard-duration" v-model.number="form.chargeDurationMinutes" type="number" inputmode="numeric" min="0"
              class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 p-2 text-sm" />
          </div>
          <div>
            <label for="wizard-power" class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.max_power') }}</label>
            <input id="wizard-power" v-model.number="form.maxChargingPowerKw" type="number" inputmode="decimal" min="0" step="0.1"
              class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 p-2 text-sm" />
          </div>
        </div>
      </div>
    </details>
  </div>
</template>

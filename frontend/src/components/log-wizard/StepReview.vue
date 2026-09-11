<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/LogFormFields.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { WizardStep } from './wizardLogic'
import SegmentToggle from './SegmentToggle.vue'

const props = defineProps<{ placeLabel: string; error: string | null }>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ goto: [step: WizardStep] }>()
const { t } = useI18n()
const { formatDistance, formatCurrency, formatNumber } = useLocaleFormat()

const pad = (n: number) => String(n).padStart(2, '0')
const nowLocal = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}` }

const energy = computed(() => {
  const v = form.value.kwhCharged ?? form.value.kwhAtVehicle
  return v == null ? '-' : `${formatNumber(v)} kWh`
})
const tiles = computed(() => [
  { label: t('logwizard.place'), value: props.placeLabel, step: 1 as WizardStep },
  { label: t('logfields.energy'), value: energy.value, step: 2 as WizardStep },
  { label: t('logfields.odometer'), value: form.value.odometerKm != null ? formatDistance(form.value.odometerKm) : '-', step: 3 as WizardStep },
  { label: t('logfields.soc_after'), value: form.value.socAfterChargePercent != null ? `${form.value.socAfterChargePercent} %` : '-', step: 3 as WizardStep },
  { label: t('logfields.cost_eur'), value: form.value.costEur != null ? formatCurrency(form.value.costEur) : '-', step: 4 as WizardStep },
])
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 gap-2">
      <button v-for="tile in tiles" :key="tile.label" type="button" @click="emit('goto', tile.step)"
        class="text-left p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60">
        <span class="block text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ tile.label }}</span>
        <b class="block text-base font-semibold tabular-nums text-gray-800 dark:text-gray-100 truncate">{{ tile.value }}</b>
        <span class="text-xs text-indigo-600 dark:text-indigo-300">{{ t('logwizard.change') }}</span>
      </button>
    </div>

    <details open class="rounded-sm border border-gray-200 dark:border-gray-700">
      <summary class="px-3 py-2.5 text-sm font-semibold cursor-pointer text-gray-800 dark:text-gray-100">
        {{ t('logwizard.more_details') }} <span class="font-normal text-gray-400">· {{ t('logfields.optional') }}</span>
      </summary>
      <div class="px-3 pb-3 space-y-4">
        <div>
          <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.timestamp') }}</label>
          <input v-model="form.loggedAt" type="datetime-local" :max="nowLocal()"
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

    <p v-if="error" class="text-sm text-red-500 dark:text-red-400 text-center">{{ error }}</p>
    <p class="inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><BoltIcon class="h-4 w-4" />{{ t('logwizard.watt_hint') }}</p>
  </div>
</template>

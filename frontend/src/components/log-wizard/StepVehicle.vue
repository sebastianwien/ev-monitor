<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/LogFormFields.vue'
import { useCountryStore } from '../../stores/country'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'
import { netEnergyKwh, socToKwh } from './wizardLogic'
import BigInput from './BigInput.vue'

const props = defineProps<{ lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined }>()
const form = defineModel<LogFormData>({ required: true })
const { t } = useI18n()
const countryStore = useCountryStore()
const { formatDistance, formatNumber } = useLocaleFormat()

const usesMiles = computed(() => countryStore.unitSystem.distanceUnit === 'miles')
const odometer = computed({
  get: () => form.value.odometerKm == null ? null : odometerKmToLocal(form.value.odometerKm, usesMiles.value),
  set: (v) => { form.value.odometerKm = v == null ? null : odometerLocalToKm(v, usesMiles.value) },
})
const showBefore = ref(form.value.socBeforeChargePercent != null)

const belowLast = computed(() => props.lastOdometerKm != null && form.value.odometerKm != null && form.value.odometerKm < props.lastOdometerKm)
const fmt1 = (n: number) => formatNumber(Math.round(n * 10) / 10)
const battery = computed(() => {
  const net = netEnergyKwh(form.value.socBeforeChargePercent, form.value.socAfterChargePercent, props.effectiveCapacityKwh)
  if (net != null) {
    const charged = form.value.kwhCharged
    const loss = charged != null ? Math.max(0, charged - net) : null
    return t('logwizard.soc_net', { net: fmt1(net), loss: loss != null ? fmt1(loss) : '-' })
  }
  const inBattery = socToKwh(form.value.socAfterChargePercent, props.effectiveCapacityKwh)
  return inBattery != null ? t('logwizard.soc_in_battery', { kwh: fmt1(inBattery), cap: fmt1(props.effectiveCapacityKwh!) }) : ''
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <label for="wizard-odometer" class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logfields.odometer') }}</label>
      <BigInput id="wizard-odometer" v-model="odometer" :unit="usesMiles ? t('logfields.unit_miles') : t('logfields.unit_km')"
        :placeholder="lastOdometerKm != null ? String(Math.round(odometerKmToLocal(lastOdometerKm, usesMiles))) : ''" step="1" :min="0" inputmode="numeric" autofocus />
      <p v-if="belowLast" class="mt-1 text-xs text-red-500">{{ t('logform.odometer_min', { min: formatDistance(lastOdometerKm!) }) }}</p>
      <p v-else-if="lastOdometerKm != null" class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ t('logform.odometer_last', { km: formatDistance(lastOdometerKm) }) }}</p>
    </div>

    <div>
      <label for="wizard-soc" class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logfields.soc_after') }}</label>
      <BigInput id="wizard-soc" v-model="form.socAfterChargePercent" unit="%" placeholder="80" step="1" :min="0" :max="100" inputmode="numeric" />
      <div class="flex gap-2 mt-2">
        <button v-for="p in [80, 90, 100]" :key="p" type="button" @click="form.socAfterChargePercent = p"
          :class="['px-3 py-1.5 rounded-full text-sm border', form.socAfterChargePercent === p ? 'bg-indigo-600 text-white border-indigo-600' : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200']">
          {{ p }} %
        </button>
      </div>
    </div>

    <div v-if="showBefore">
      <label for="wizard-soc-before" class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logfields.soc_before') }}</label>
      <BigInput id="wizard-soc-before" v-model="form.socBeforeChargePercent" unit="%" placeholder="20" step="1" :min="0" :max="100" inputmode="numeric" />
    </div>
    <button v-else type="button" @click="showBefore = true"
      class="px-3 py-1.5 rounded-full text-sm border border-dashed border-gray-400 text-gray-600 dark:text-gray-300">
      + {{ t('logwizard.soc_before_cta') }}
    </button>

    <p v-if="battery" class="text-sm text-gray-500 dark:text-gray-400 tabular-nums">{{ battery }}</p>
  </div>
</template>

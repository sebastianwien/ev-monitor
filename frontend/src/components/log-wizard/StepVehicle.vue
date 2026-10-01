<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { useCountryStore } from '../../stores/country'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'
import { netEnergyKwh, socToKwh } from './wizardLogic'
import RulerInput from './RulerInput.vue'

const props = defineProps<{ lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined; compact?: boolean }>()
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
// Der Maßstab beginnt beim letzten Stand - absolute Zahl bleibt der Wert, das Delta nur die Unterzeile
const odoMin = computed(() => props.lastOdometerKm != null ? Math.round(odometerKmToLocal(props.lastOdometerKm, usesMiles.value)) : 0)
const odoMax = computed(() => odoMin.value + (props.lastOdometerKm != null ? 1500 : 999_999))
const odoSub = computed(() => {
  if (belowLast.value) return t('logform.odometer_min', { min: formatDistance(props.lastOdometerKm!) })
  if (props.lastOdometerKm == null) return null
  if (form.value.odometerKm == null) return t('logform.odometer_last', { km: formatDistance(props.lastOdometerKm) })
  return t('logwizard.odometer_delta', { km: formatDistance(form.value.odometerKm - props.lastOdometerKm) })
})
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
  <div class="space-y-3">
    <RulerInput id="wizard-odometer" v-model="odometer" :label="t('logfields.odometer')" :unit="usesMiles ? t('logfields.unit_miles') : t('logfields.unit_km')"
      :placeholder="lastOdometerKm != null ? String(odoMin) : ''" :step="1" :min="odoMin" :max="odoMax" :px-per-step="7" :label-every="50"
      inputmode="numeric" :sub="odoSub" :sub-tone="belowLast ? 'warn' : 'muted'" :autofocus="!compact" />

    <div class="space-y-2">
      <RulerInput id="wizard-soc" v-model="form.socAfterChargePercent" :label="t('logfields.soc_after')" unit="%" placeholder="80" :step="1" :min="0" :max="100" :px-per-step="10" inputmode="numeric" />
      <div :class="CHIP_ROW">
        <button v-for="p in [80, 90, 100]" :key="p" type="button" @click="form.socAfterChargePercent = p"
          :class="chipClass(form.socAfterChargePercent === p)">
          {{ p }} %
        </button>
      </div>
    </div>

    <RulerInput v-if="showBefore" id="wizard-soc-before" v-model="form.socBeforeChargePercent" :label="t('logfields.soc_before')" unit="%" placeholder="20" :step="1" :min="0" :max="100" :px-per-step="10" inputmode="numeric" />
    <div v-else :class="CHIP_ROW">
      <button type="button" @click="showBefore = true" :class="chipClass(false, 'dashed')">
        + {{ t('logwizard.soc_before_cta') }}
      </button>
    </div>

    <p v-if="battery" class="text-sm text-gray-500 dark:text-gray-400 tabular-nums">{{ battery }}</p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'

/**
 * Schritt 2: nur die Werte, die kein früheres Log liefern kann - Energie, Tacho, Akku, Preis.
 * Ein Screen statt drei: die Teilschritte bleiben eigene Komponenten (der Bearbeiten-Dialog
 * nutzt sie einzeln), hier stehen sie kompakt untereinander.
 */
defineProps<{ cost: ReturnType<typeof useCostInput>; lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined }>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
const { t } = useI18n()
</script>

<template>
  <div class="space-y-5">
    <section>
      <h2 class="mb-2 text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logwizard.q_energy') }}</h2>
      <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
    </section>
    <section>
      <h2 class="mb-2 text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logwizard.q_vehicle') }}</h2>
      <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
    </section>
    <section>
      <h2 class="mb-2 text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logwizard.q_cost') }}</h2>
      <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact />
    </section>
  </div>
</template>

<script setup lang="ts">
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
defineProps<{ cost: ReturnType<typeof useCostInput>; lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined; openCard?: 'new' | 'price' | null }>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
</script>

<template>
  <!-- Vier Zeilen, kein Abschnittstitel: die Feldbeschriftung sagt schon, was gefragt ist -->
  <div class="space-y-2">
    <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
    <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
    <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact :open-on-mount="openCard" />
  </div>
</template>

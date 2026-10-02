<script setup lang="ts">
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import type { ConsumptionPreview } from '../../utils/consumptionPreview'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
import OptionalPills from './OptionalPills.vue'
import PlaceHeader, { type NumbersContext } from './PlaceHeader.vue'

export type { NumbersContext }

/**
 * Schritt 2: nur die Werte, die kein früheres Log liefern kann - Energie, Tacho, Akku, Preis.
 * Ein Screen statt drei: die Teilschritte bleiben eigene Komponenten (der Bearbeiten-Dialog
 * nutzt sie einzeln), hier stehen sie kompakt untereinander. Sobald Tacho und kWh stehen,
 * zeigt die Preiszeile Verbrauch und Kosten je 100 km seit der letzten Ladung. Darunter die optionalen Angaben.
 */
const props = defineProps<{
  cost: ReturnType<typeof useCostInput>; lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined
  openCard?: 'new' | 'price' | null; context: NumbersContext; preview: ConsumptionPreview | null
}>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
</script>

<template>
  <!-- Ohne Karte: ein Held pro Schritt, hier die Zahlen. Der Ort steht als Zeile darüber, der freie Platz bleibt frei (Daumenzone). -->
  <div class="space-y-3">
    <PlaceHeader :context="context" :map="false" />

    <div class="space-y-2">
      <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
      <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
      <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact :open-on-mount="openCard" :preview="preview" />
      <OptionalPills v-model="form" />
    </div>
  </div>
</template>

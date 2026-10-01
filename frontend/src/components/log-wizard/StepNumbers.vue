<script setup lang="ts">
import { ref } from 'vue'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import type { ConsumptionPreview } from '../../utils/consumptionPreview'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
import OptionalPanel from './OptionalPanel.vue'
import PlaceHeader, { type NumbersContext } from './PlaceHeader.vue'
import { useFillHeight } from '../../composables/useFillHeight'

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


const root = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const mapHeight = useFillHeight(root, body)
</script>

<template>
  <div ref="root" class="space-y-2">
    <PlaceHeader :context="context" :height="mapHeight" />

    <div ref="body" class="space-y-2">
      <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
      <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
      <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact :open-on-mount="openCard" :preview="preview" />
      <OptionalPanel v-model="form" />
    </div>
  </div>
</template>

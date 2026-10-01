<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import { consumptionPreview, type PreviousLogRef } from '../../utils/consumptionPreview'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
import OptionalPanel from './OptionalPanel.vue'
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
  openCard?: 'new' | 'price' | null; context: NumbersContext; previousLog: PreviousLogRef | null
}>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const emit = defineEmits<{ ocr: [result: any] }>()

const preview = computed(() => consumptionPreview({
  kwhCharged: form.value.kwhCharged, kwhAtVehicle: form.value.kwhAtVehicle, chargingType: form.value.chargingType,
  odometerKm: form.value.odometerKm, socAfter: form.value.socAfterChargePercent, capacityKwh: props.effectiveCapacityKwh,
  costEur: form.value.costEur, previous: props.previousLog,
}))

// ── Kartenhöhe: füllt den freien Platz zwischen Kopfzeile und Eingaben, animiert, wenn die Eingaben
// wachsen (aufgeklapptes Rädchen, Ladekarte, Details). Gemessen wird der Scrollbereich der Shell,
// nicht das eigene Element - das wüchse sonst mit der Karte und die Messung bisse sich in den Schwanz.
const MAP_MIN = 112, MAP_MAX = 640
const root = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const mapHeight = ref(MAP_MIN)
let sizeWatch: ResizeObserver | null = null
const measure = () => {
  const scroller = root.value?.parentElement?.parentElement
  if (!scroller || !body.value) return
  const pad = parseFloat(getComputedStyle(root.value!.parentElement!).paddingBottom) || 0
  const free = scroller.clientHeight - pad - body.value.offsetHeight
  mapHeight.value = Math.max(MAP_MIN, Math.min(MAP_MAX, free))
}
onMounted(() => {
  if (typeof ResizeObserver === 'undefined') return
  sizeWatch = new ResizeObserver(measure)
  if (body.value) sizeWatch.observe(body.value)
  const scroller = root.value?.parentElement?.parentElement
  if (scroller) sizeWatch.observe(scroller)
})
onBeforeUnmount(() => sizeWatch?.disconnect())
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

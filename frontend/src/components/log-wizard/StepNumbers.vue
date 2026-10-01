<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { MapPinIcon, CreditCardIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import { consumptionPreview, type PreviousLogRef } from '../../utils/consumptionPreview'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
import OptionalPanel from './OptionalPanel.vue'
const PlaceMinimap = defineAsyncComponent(() => import('./PlaceMinimap.vue'))

/** Was Schritt 1 ergeben hat - steht als Kopf über den Zahlen, damit niemand zurückblättern muss. */
export interface NumbersContext { title: string; address: string | null; card: string | null; lat: number | null; lon: number | null }

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
const hasMap = computed(() => props.context.lat != null && props.context.lon != null)

// ── Kartenhöhe: füllt den freien Platz zwischen Kopfzeile und Eingaben, animiert, wenn die Eingaben
// wachsen (aufgeklapptes Rädchen, Ladekarte, Details). Gemessen wird der Scrollbereich der Shell,
// nicht das eigene Element - das wüchse sonst mit der Karte und die Messung bisse sich in den Schwanz.
const MAP_MIN = 112, MAP_MAX = 420
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
    <!-- Kopf: Minimap mit Säule, Adresse und Karte eingeblendet; ohne Position nur die Textzeile. -->
    <div data-testid="numbers-context" class="relative -mx-4 md:mx-0 md:rounded-sm overflow-hidden transition-[height] duration-300 ease-out motion-reduce:transition-none"
      :style="hasMap ? { height: `${mapHeight}px` } : undefined">
      <PlaceMinimap v-if="hasMap" :lat="context.lat!" :lon="context.lon!" class="absolute inset-0" />
      <div :class="hasMap ? 'absolute inset-x-0 bottom-0 z-[500] bg-gradient-to-t from-white via-white/90 to-transparent dark:from-gray-900 dark:via-gray-900/90 px-4 pt-6 pb-1' : 'px-4'">
        <div class="flex items-end justify-between gap-3">
          <div class="min-w-0">
            <p class="flex items-center gap-1.5 text-sm font-semibold text-gray-800 dark:text-gray-100 truncate"><MapPinIcon class="h-4 w-4 flex-shrink-0 text-indigo-600 dark:text-indigo-400" />{{ context.title }}</p>
            <p v-if="context.address" class="text-xs text-gray-500 dark:text-gray-400 truncate pl-[1.375rem]">{{ context.address }}</p>
          </div>
          <p v-if="context.card" class="flex items-center gap-1 flex-shrink-0 text-xs text-gray-600 dark:text-gray-300"><CreditCardIcon class="h-4 w-4" />{{ context.card }}</p>
        </div>
      </div>
    </div>

    <div ref="body" class="space-y-2">
      <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
      <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
      <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact :open-on-mount="openCard" :preview="preview" />
      <OptionalPanel v-model="form" />
    </div>
  </div>
</template>

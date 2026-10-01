<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapPinIcon, CreditCardIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { consumptionPreview, type PreviousLogRef } from '../../utils/consumptionPreview'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
const PlaceMinimap = defineAsyncComponent(() => import('./PlaceMinimap.vue'))

/** Was Schritt 1 ergeben hat - steht als Kopf über den Zahlen, damit niemand zurückblättern muss. */
export interface NumbersContext { title: string; address: string | null; card: string | null; lat: number | null; lon: number | null }

/**
 * Schritt 2: nur die Werte, die kein früheres Log liefern kann - Energie, Tacho, Akku, Preis.
 * Ein Screen statt drei: die Teilschritte bleiben eigene Komponenten (der Bearbeiten-Dialog
 * nutzt sie einzeln), hier stehen sie kompakt untereinander. Sobald Tacho und kWh stehen,
 * erscheint unten ein Richtwert für Verbrauch und Kosten je 100 km seit der letzten Ladung.
 */
const props = defineProps<{
  cost: ReturnType<typeof useCostInput>; lastOdometerKm: number | null; effectiveCapacityKwh: number | null | undefined
  openCard?: 'new' | 'price' | null; context: NumbersContext; previousLog: PreviousLogRef | null
}>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
const { t } = useI18n()
const { formatConsumption, formatCurrency, formatDistance } = useLocaleFormat()

const preview = computed(() => consumptionPreview({
  kwhCharged: form.value.kwhCharged, kwhAtVehicle: form.value.kwhAtVehicle, chargingType: form.value.chargingType,
  odometerKm: form.value.odometerKm, socAfter: form.value.socAfterChargePercent, capacityKwh: props.effectiveCapacityKwh,
  costEur: form.value.costEur, previous: props.previousLog,
}))
const hasMap = computed(() => props.context.lat != null && props.context.lon != null)
</script>

<template>
  <div class="space-y-2">
    <!-- Kopf: Minimap mit Säule, Adresse und Karte eingeblendet; ohne Position nur die Textzeile. -->
    <div data-testid="numbers-context" class="relative -mx-4 md:mx-0 md:rounded-sm overflow-hidden" :class="hasMap ? 'h-28' : ''">
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

    <StepEnergy v-model="form" compact @ocr="r => emit('ocr', r)" />
    <StepVehicle v-model="form" compact :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="effectiveCapacityKwh" />
    <StepCost v-model="form" v-model:providers="providers" :cost="cost" compact :open-on-mount="openCard" />

    <p v-if="preview" data-testid="consumption-preview" class="pt-1 text-center text-xs text-gray-500 dark:text-gray-400 tabular-nums">
      <span :class="['font-semibold', preview.plausible ? 'text-gray-800 dark:text-gray-100' : 'text-amber-600 dark:text-amber-400']">≈ {{ formatConsumption(preview.kwhPer100km) }}</span>
      <template v-if="preview.eurPer100km != null"> · <span class="font-semibold text-gray-800 dark:text-gray-100">{{ formatCurrency(preview.eurPer100km) }}/100 km</span></template>
      <br>{{ t('logwizard.preview_since', { km: formatDistance(preview.distanceKm) }) }}
    </p>
  </div>
</template>

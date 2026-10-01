<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import type { WizardStep } from './wizardLogic'
import LogSummary, { type SummarySection } from './LogSummary.vue'
import PlaceHeader, { type NumbersContext } from './PlaceHeader.vue'
import { useFillHeight } from '../../composables/useFillHeight'
import type { CostMetric } from './costMetrics'

defineProps<{ placeLabel: string; error: string | null; context: NumbersContext; costMetrics: CostMetric[] }>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ goto: [step: WizardStep] }>()
const { t } = useI18n()

const root = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const mapHeight = useFillHeight(root, body)
const stepFor: Record<SummarySection, WizardStep> = { place: 1, energy: 2, vehicle: 2, cost: 2, time: 3 }
</script>

<template>
  <div ref="root" class="space-y-4">
    <!-- Karte füllt den Platz unter der Kopfzeile wie in Schritt 2; Tipp darauf springt zum Ort -->
    <PlaceHeader :context="context" :height="mapHeight" clickable data-testid="summary-place" @click="emit('goto', 1)" />
    <div ref="body" class="space-y-4">
      <LogSummary v-model="form" :place-label="placeLabel" hide-place details-numbers-only :cost-metrics="costMetrics" @edit="s => emit('goto', stepFor[s])" />
      <p v-if="error" class="text-sm text-red-500 dark:text-red-400 text-center">{{ error }}</p>
      <p class="inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><BoltIcon class="h-4 w-4" />{{ t('logwizard.watt_hint') }}</p>
    </div>
  </div>
</template>

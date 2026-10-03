<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import type { RequiredField, VoiceFlag, VoiceUsage, WizardStep } from './wizardLogic'
import VoiceTranscript from './VoiceTranscript.vue'
import LogSummary, { type SummarySection } from './LogSummary.vue'
import PlaceHeader, { type NumbersContext } from './PlaceHeader.vue'
import { useFillHeight } from '../../composables/useFillHeight'
import { MINIMAP_MAX_PX } from './minimapTiles'
import type { CostMetric } from './costMetrics'

const props = defineProps<{
  placeLabel: string; error: string | null; context: NumbersContext; costMetrics: CostMetric[]
  /** Nach einer Sprachaufnahme: das Gehörte und die unsicheren Felder */
  voice?: { transcript: string; flags: VoiceFlag[]; usage: VoiceUsage } | null
}>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ goto: [step: WizardStep] }>()
const { t } = useI18n()

const root = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const mapHeight = useFillHeight(root, body, 112, MINIMAP_MAX_PX)
const TILE_FLAGS: VoiceFlag[] = ['energy', 'odometer', 'soc', 'cost']
const flagged = computed(() => (props.voice?.flags ?? []).filter((f): f is RequiredField => TILE_FLAGS.includes(f)))
const stepFor: Record<SummarySection, WizardStep> = { place: 1, energy: 2, vehicle: 2, cost: 2, time: 3 }
</script>

<template>
  <div ref="root" class="space-y-4">
    <!-- Karte füllt den Platz unter der Kopfzeile wie in Schritt 2; Tipp darauf springt zum Ort -->
    <PlaceHeader :context="context" :height="mapHeight" clickable data-testid="summary-place" @click="emit('goto', 1)" />
    <div ref="body" class="space-y-4">
      <VoiceTranscript v-if="voice" :transcript="voice.transcript" :flags="voice.flags" :usage="voice.usage" />
      <LogSummary :flagged="flagged" v-model="form" :place-label="placeLabel" hide-place details-numbers-only reveal :cost-metrics="costMetrics" @edit="s => emit('goto', stepFor[s])" />
      <p v-if="error" class="text-sm text-red-500 dark:text-red-400 text-center">{{ error }}</p>
      <p class="inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><BoltIcon class="h-4 w-4" />{{ t('logwizard.watt_hint') }}</p>
    </div>
  </div>
</template>

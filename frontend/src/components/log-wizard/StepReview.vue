<script setup lang="ts">
import { computed, ref } from 'vue'
import type { LogFormData } from '../log-form/logFormData'
import type { RequiredField, VoiceFlag, VoiceUsage, WizardStep } from './wizardLogic'
import VoiceTranscript from './VoiceTranscript.vue'
import LogSummary, { type SummarySection } from './LogSummary.vue'
import HighwayQuestion from './HighwayQuestion.vue'
import PlaceHeader, { type NumbersContext } from './PlaceHeader.vue'
import { useFillHeight } from '../../composables/useFillHeight'
import { MINIMAP_MAX_PX } from './minimapTiles'
import type { CostMetric } from './costMetrics'

const props = defineProps<{
  placeLabel: string; error: string | null; context: NumbersContext; costMetrics: CostMetric[]
  /** Nachfrage nach Autobahn, wenn seit der letzten Ladung viel Energie in kurzer Zeit verbraucht wurde */
  askHighway?: boolean
  /** Nach einer Sprachaufnahme: das Gehörte und die unsicheren Felder */
  voice?: { transcript: string; flags: VoiceFlag[]; usage: VoiceUsage } | null
}>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ goto: [step: WizardStep] }>()

const root = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const mapHeight = useFillHeight(root, body, 112, MINIMAP_MAX_PX)
const TILE_FLAGS: VoiceFlag[] = ['energy', 'odometer', 'soc', 'cost']
const flagged = computed(() => (props.voice?.flags ?? []).filter((f): f is RequiredField => TILE_FLAGS.includes(f)))
const stepFor: Record<SummarySection, WizardStep> = { place: 1, energy: 2, vehicle: 2, cost: 2 }
</script>

<template>
  <div ref="root" class="space-y-4">
    <!-- Karte füllt den Platz unter der Kopfzeile wie in Schritt 2; Tipp darauf springt zum Ort -->
    <PlaceHeader :context="context" :height="mapHeight" clickable data-testid="summary-place" @click="emit('goto', 1)" />
    <div ref="body" class="space-y-4">
      <VoiceTranscript v-if="voice" :transcript="voice.transcript" :flags="voice.flags" :usage="voice.usage">
        <slot name="voice-action" />
      </VoiceTranscript>
      <HighwayQuestion v-if="askHighway" v-model="form" />
      <LogSummary :flagged="flagged" v-model="form" :place-label="placeLabel" hide-place details-numbers-only reveal :cost-metrics="costMetrics" @edit="s => emit('goto', stepFor[s])" />
      <p v-if="error" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ error }}</p>
    </div>
  </div>
</template>

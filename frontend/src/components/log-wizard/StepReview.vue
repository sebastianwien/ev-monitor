<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import type { WizardStep } from './wizardLogic'
import LogSummary, { type SummarySection } from './LogSummary.vue'
import type { NumbersContext } from './PlaceHeader.vue'

defineProps<{ placeLabel: string; error: string | null; context: NumbersContext }>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ goto: [step: WizardStep] }>()
const { t } = useI18n()

const stepFor: Record<SummarySection, WizardStep> = { place: 1, energy: 2, vehicle: 2, cost: 2, time: 3 }
</script>

<template>
  <div class="space-y-4">
    <LogSummary v-model="form" :place-label="placeLabel" :context="context" @edit="s => emit('goto', stepFor[s])" />
    <p v-if="error" class="text-sm text-red-500 dark:text-red-400 text-center">{{ error }}</p>
    <p class="inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><BoltIcon class="h-4 w-4" />{{ t('logwizard.watt_hint') }}</p>
  </div>
</template>

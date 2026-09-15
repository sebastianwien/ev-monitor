<script setup lang="ts">
import { chipClass } from './chipClass'
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import BigInput from './BigInput.vue'
import SegmentToggle from './SegmentToggle.vue'
const OcrPhotoCapture = defineAsyncComponent(() => import('../log-form/OcrPhotoCapture.vue'))

const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
const { t } = useI18n()

const mode = ref<'charger' | 'vehicle'>(form.value.kwhAtVehicle && !form.value.kwhCharged ? 'vehicle' : 'charger')
const showOcr = ref(false)

const kwh = computed({
  get: () => mode.value === 'charger' ? form.value.kwhCharged : form.value.kwhAtVehicle,
  set: (v) => { if (mode.value === 'charger') form.value.kwhCharged = v; else form.value.kwhAtVehicle = v },
})
// Säule und Fahrzeug sind zwei getrennte Werte: beim Umschalten wandert nichts mit,
// beide bleiben erhalten und werden zusammen gespeichert (Brutto + Netto).
const switchMode = (m: 'charger' | 'vehicle') => { mode.value = m }
const onOcr = (r: any) => { showOcr.value = false; mode.value = 'charger'; emit('ocr', r) }
</script>

<template>
  <!-- Lese-Zone oben (Erklaerung, seltene Foto-Aktion), Bedien-Zone unten in Daumenreichweite.
       Der Freiraum dazwischen trennt Lesen von Bedienen, statt als Loch ueber dem Inhalt zu stehen. -->
  <div class="flex-1 flex flex-col gap-4">
    <div class="space-y-3">
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ mode === 'charger' ? t('logfields.kwh_hint') : t('logfields.kwh_at_vehicle_hint') }}</p>
      <button type="button" @click="showOcr = !showOcr"
        class="btn-3d inline-flex items-center gap-2 min-h-11 px-4 py-2 rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700">
        <CameraIcon class="h-4 w-4" />{{ t('logwizard.ocr_cta') }}
      </button>
      <OcrPhotoCapture v-if="showOcr" @dataExtracted="onOcr" @cancel="showOcr = false" />
    </div>

    <div class="mt-auto space-y-4">
      <SegmentToggle :model-value="mode" @update:model-value="switchMode"
        :options="[{ value: 'charger', label: t('logwizard.kwh_charger'), testid: 'kwh-mode-charger' }, { value: 'vehicle', label: t('logwizard.kwh_vehicle'), testid: 'kwh-mode-vehicle' }]" />
      <!-- Ladeart: aus der Ortswahl vorbelegt (Säule DC, sonst AC), hier korrigierbar,
           weil sie die Ladeverluste bestimmt, die der Hinweis oben erklärt. -->
      <div class="flex items-center justify-end gap-2">
        <span class="mr-auto text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logwizard.charging_type') }}</span>
        <button v-for="ct in (['AC', 'DC'] as const)" :key="ct" type="button" :aria-pressed="form.chargingType === ct"
          :data-testid="`charging-type-${ct.toLowerCase()}`" @click="form.chargingType = ct" :class="chipClass(form.chargingType === ct)">
          {{ ct }}
        </button>
      </div>
      <BigInput id="wizard-kwh" v-model="kwh" unit="kWh" :label="t('logfields.energy')" :placeholder="t('logfields.kwh_placeholder')" step="0.1" :min="0" autofocus />
    </div>
  </div>
</template>

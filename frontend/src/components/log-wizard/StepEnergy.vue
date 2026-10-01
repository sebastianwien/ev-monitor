<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import RulerInput from './RulerInput.vue'
import AcDcSwitch from './AcDcSwitch.vue'
import ChargingPileIcon from '../icons/ChargingPileIcon.vue'
import CarIcon from '../icons/CarIcon.vue'
const OcrPhotoCapture = defineAsyncComponent(() => import('../log-form/OcrPhotoCapture.vue'))
import Collapse from './Collapse.vue'

const props = defineProps<{ compact?: boolean }>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ ocr: [result: any] }>()
const { t } = useI18n()

const mode = ref<'charger' | 'vehicle'>(form.value.kwhAtVehicle && !form.value.kwhCharged ? 'vehicle' : 'charger')
const showOcr = ref(false)
const modes = computed(() => [
  { value: 'charger' as const, label: t('logwizard.kwh_charger'), icon: ChargingPileIcon, testid: 'kwh-mode-charger' },
  { value: 'vehicle' as const, label: t('logwizard.kwh_vehicle'), icon: CarIcon, testid: 'kwh-mode-vehicle' },
])
const modeLabel = computed(() => mode.value === 'charger' ? t('logwizard.kwh_charger') : t('logwizard.kwh_vehicle'))

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
  <div :class="props.compact ? 'space-y-2' : 'flex-1 flex flex-col gap-4'">
    <!-- Nur im vollen Layout: kompakt wäre das ein leeres Div, dessen space-y-Abstand als 8-px-Lücke über die Karte durchschlägt -->
    <div v-if="!props.compact" class="space-y-3">
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ mode === 'charger' ? t('logfields.kwh_hint') : t('logfields.kwh_at_vehicle_hint') }}</p>
      <button type="button" @click="showOcr = !showOcr"
        class="btn-3d inline-flex items-center gap-2 min-h-11 px-4 py-2 rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700">
        <CameraIcon class="h-4 w-4" />{{ t('logwizard.ocr_cta') }}
      </button>
      <OcrPhotoCapture v-if="showOcr" @dataExtracted="onOcr" @cancel="showOcr = false" />
    </div>

    <div :class="props.compact ? 'space-y-2' : 'mt-auto space-y-4'">
      <!-- Quelle und Ladeart in einer Zeile - spart Hoehe. Ladeart aus der Ortswahl vorbelegt
           (Säule DC, sonst AC), hier korrigierbar, weil sie die Ladeverluste bestimmt. -->
      <div class="flex items-center justify-between gap-2">
        <!-- Quelle der kWh als Icon-Paar: Säule (brutto) oder Auto (netto). Der Feldname darunter sagt die Wahl in Worten. -->
        <div role="radiogroup" :aria-label="t('logfields.energy')" class="grid grid-cols-2 gap-1 h-11 w-[5.5rem] rounded-full bg-gray-200 dark:bg-gray-700 p-1">
          <button v-for="m in modes" :key="m.value" type="button" role="radio" :aria-checked="mode === m.value" :aria-label="m.label" :title="m.label"
            :data-testid="m.testid" @click="switchMode(m.value)"
            :class="['flex items-center justify-center rounded-full transition-colors', mode === m.value ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow' : 'text-gray-500 dark:text-gray-300']">
            <component :is="m.icon" class="h-6 w-6" />
          </button>
        </div>
        <!-- Seltene Aktion: Beleg fotografieren sitzt im Leerraum zwischen den Schaltern statt in eigener Zeile -->
        <button v-if="props.compact" type="button" @click="showOcr = !showOcr" :aria-label="t('logwizard.ocr_cta')" :title="t('logwizard.ocr_cta')"
          :class="['flex items-center justify-center h-11 w-11 rounded-full transition-colors', showOcr ? 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-indigo-600']">
          <CameraIcon class="h-6 w-6" />
        </button>
        <AcDcSwitch v-model="form.chargingType" />
      </div>
      <Collapse :open="props.compact && showOcr">
        <OcrPhotoCapture v-if="showOcr" @dataExtracted="onOcr" @cancel="showOcr = false" />
      </Collapse>
      <RulerInput id="wizard-kwh" v-model="kwh" unit="kWh" :label="modeLabel" :placeholder="t('logfields.kwh_placeholder')" :step="0.1" :min="0" :max="150" :autofocus="!props.compact" />
    </div>
  </div>
</template>

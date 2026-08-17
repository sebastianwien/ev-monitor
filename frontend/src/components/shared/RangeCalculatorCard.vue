<template>
  <div class="mt-6 pt-5 border-t border-gray-100 dark:border-gray-700">
    <!-- Intro: macht klar, dass hier das eigene Fahrprofil eingestellt wird -->
    <div class="text-center mb-4">
      <p class="text-xl font-bold text-gray-800 dark:text-gray-200 flex items-center justify-center gap-1.5">
        <MapPinIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
        {{ t('model.range_calc_title') }}
      </p>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('model.range_calc_hint') }}</p>
    </div>

    <!-- Live-Ergebnis: die Reichweite ist die Antwort, Verbrauch/Fenster nur die Annahme -->
    <div class="text-center mb-4">
      <div class="flex items-baseline justify-center gap-1.5">
        <span class="text-4xl sm:text-5xl font-extrabold tabular-nums text-green-700 dark:text-green-400 leading-none">
          {{ rangeKm != null ? formatDistance(rangeKm, { showUnit: false }) : '-' }}
        </span>
        <span class="text-base sm:text-xl font-bold text-gray-800 dark:text-gray-200">{{ distanceUnitLabel() }}</span>
      </div>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-1.5 tabular-nums">
        {{ formatConsumption(consumption) }} · {{ windowLabel(activeWindow) }}
      </p>
    </div>

    <!-- Verbrauchs-Slider mit Marken aus den echten Modelldaten -->
    <div class="flex items-center gap-3">
      <span class="hidden sm:inline text-xs font-medium text-gray-500 dark:text-gray-400 shrink-0 tabular-nums">
        {{ formatConsumption(scale.min, { showUnit: false }) }}
      </span>
      <input
        type="range"
        :min="scale.min"
        :max="scale.max"
        step="0.1"
        v-model.number="consumption"
        @input="onSliderInput"
        :aria-label="t('model.range_calc_consumption_label')"
        :aria-valuetext="formatConsumption(consumption)"
        :style="{ '--pct': fillPct + '%' }"
        class="ev-slider flex-1" />
      <span class="hidden sm:inline text-xs font-medium text-gray-500 dark:text-gray-400 shrink-0 tabular-nums">
        {{ formatConsumption(scale.max, { showUnit: false }) }}
      </span>
    </div>

    <!-- Marken-Chips: springen auf einen belegten Verbrauchswert (WLTP, Ø, Sommer, Winter) -->
    <div v-if="markers.length" class="flex flex-wrap justify-center gap-2 mt-1">
      <button
        v-for="m in markers" :key="m.key"
        type="button"
        @click="selectMarker(m)"
        :aria-pressed="isMarkerActive(m)"
        class="px-2.5 py-1 rounded-full text-xs font-semibold border transition-colors tabular-nums"
        :class="isMarkerActive(m)
          ? 'bg-green-600 border-green-700 text-white'
          : 'bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'">
        {{ m.label }} <span class="font-normal opacity-80">{{ formatConsumption(m.value, { showUnit: false }) }}</span>
      </button>
    </div>

    <!-- Ladefenster: diskrete Werte, daher Chips statt Slider (auf Mobile tappbar statt ziehbar) -->
    <div class="mt-5">
      <div class="flex items-center justify-center gap-1.5 mb-2">
        <Battery0Icon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        <span class="text-xs font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          {{ t('model.range_calc_window_label') }}
        </span>
      </div>
      <div role="group" :aria-label="t('model.range_calc_window_label')" class="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <button
          v-for="(w, i) in CHARGE_WINDOWS" :key="`${w.from}-${w.to}`"
          type="button"
          @click="selectWindow(i)"
          :aria-pressed="i === windowIndex"
          class="py-2 rounded-lg text-sm font-semibold border-2 transition-colors tabular-nums"
          :class="i === windowIndex
            ? 'bg-green-600 border-gray-800 dark:border-gray-200 text-white'
            : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'">
          {{ windowLabel(w) }}
        </button>
      </div>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-2 text-center">{{ t('model.range_calc_window_hint') }}</p>
    </div>

    <!-- Loest den Kern-Konflikt: der sparsame Fahrer und der Schnitt sind beide echt -->
    <p v-if="communitySpan" class="text-sm text-gray-700 dark:text-gray-300 mt-4 text-center max-w-md mx-auto">
      {{ t('model.range_calc_community_span', {
        min: formatDistance(communitySpan.min, { showUnit: false }),
        max: formatDistance(communitySpan.max),
      }) }}
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * Reichweiten-Rechner der Modell-Detailseite.
 *
 * Beantwortet "wie weit komme ich damit" nicht mit einer Zahl, sondern mit einer
 * einstellbaren: Verbrauch per Slider (mit Marken aus WLTP/Community/Sommer/Winter),
 * Ladefenster per Chips. Wer im Forum von 400 km berichtet, kann seinen Wert hier
 * reproduzieren statt dem Community-Schnitt widersprechen zu muessen.
 *
 * Rechnet intern in kWh/100km; die Anzeige uebersetzt nach mi/kWh, wo das Land
 * es verlangt.
 */
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapPinIcon, Battery0Icon } from '@heroicons/vue/24/outline'
import {
  CHARGE_WINDOWS, type ChargeWindow, type RangeMarker,
  calcRangeKm, buildConsumptionScale, clampConsumption,
} from '../../utils/rangeCalculator'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useHaptic } from '../../composables/useHaptic'

const props = defineProps<{
  /** Netto-Kapazitaet der gewaehlten Variante in kWh. */
  netCapacityKwh: number
  /** Startwert des Sliders (typisch der Community-Schnitt), kWh/100km. */
  defaultConsumption: number
  markers: RangeMarker[]
  /** Sparsamster bzw. hungrigster Community-Wert, kWh/100km - fuer die Spannen-Zeile. */
  minConsumption?: number | null
  maxConsumption?: number | null
}>()

const { t } = useI18n()
const { formatConsumption, formatDistance, distanceUnitLabel } = useLocaleFormat()
const { haptic } = useHaptic()

const scale = computed(() => buildConsumptionScale([
  props.defaultConsumption,
  ...props.markers.map(m => m.value),
  props.minConsumption,
  props.maxConsumption,
]))

const consumption = ref(clampConsumption(props.defaultConsumption, scale.value))
const windowIndex = ref(0)

// Variantenwechsel bringt neue Daten - der Slider muss zurueck auf den neuen
// Schnitt, sonst zeigt er den Wert der vorher gewaehlten Variante.
watch(() => props.defaultConsumption, v => { consumption.value = clampConsumption(v, scale.value) })

const activeWindow = computed(() => CHARGE_WINDOWS[windowIndex.value])
const rangeKm = computed(() => calcRangeKm(props.netCapacityKwh, consumption.value, activeWindow.value))

const fillPct = computed(() => {
  const span = scale.value.max - scale.value.min
  if (span <= 0) return 0
  return Math.round((consumption.value - scale.value.min) / span * 100)
})

/** Reichweiten-Spanne der Community im aktuell gewaehlten Ladefenster. */
const communitySpan = computed(() => {
  // Hoher Verbrauch = kurze Reichweite, daher gekreuzt.
  const min = calcRangeKm(props.netCapacityKwh, props.maxConsumption, activeWindow.value)
  const max = calcRangeKm(props.netCapacityKwh, props.minConsumption, activeWindow.value)
  if (min == null || max == null || min === max) return null
  return { min, max }
})

const windowLabel = (w: ChargeWindow) => `${w.from} → ${w.to} %`

const isMarkerActive = (m: RangeMarker) => Math.abs(consumption.value - m.value) < 0.05

function selectMarker(m: RangeMarker) {
  consumption.value = clampConsumption(m.value, scale.value)
  haptic(8)
}

function selectWindow(i: number) {
  windowIndex.value = i
  haptic(8)
}

// Haptische "Ticks" beim Ziehen - zeitgedrosselt, damit der Vibrationsmotor bei
// schnellem Ziehen nicht ueberlastet wird (gleiche Drosselung wie beim Tarif-Slider).
let lastTick = 0
function onSliderInput() {
  const now = Date.now()
  if (now - lastTick >= 30) {
    haptic(5)
    lastTick = now
  }
}
</script>

<template>
  <!-- A question the reader was sent back to ("freischalten" with open sliders) is marked until answered -->
  <div :id="id" class="grid gap-1 rounded-2xl motion-safe:transition-shadow"
       :class="highlight ? 'ring-2 ring-green-600 ring-offset-4 ring-offset-white dark:ring-green-500 dark:ring-offset-gray-900' : ''">
    <!-- compact (the ranking's one-row strip from xl): label above the value, no min/max line -->
    <div class="flex items-baseline justify-between gap-3" :class="compact ? 'xl:grid xl:justify-start xl:gap-0.5' : ''">
      <label :for="`${id}-input`" class="min-w-0 text-[15px] font-semibold text-gray-900 dark:text-gray-100" :class="compact ? 'xl:text-[13px] xl:leading-snug xl:text-gray-700 xl:dark:text-gray-300' : ''">{{ label }}</label>
      <output :for="`${id}-input`" class="flex-none whitespace-nowrap text-[17px] font-bold tabular-nums"
              :class="shown == null ? 'font-medium text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'"
              :data-testid="`slider-${testKey}-value`">
        {{ shown == null ? t('model.page.slider_open') : format(shown) }}
      </output>
    </div>
    <!-- Shows the value live while dragging, answers on release, so the result reacts once -->
    <input :id="`${id}-input`" type="range" :min="min" :max="max" :step="step"
           :value="shown ?? start"
           class="profile-slider h-11 w-full cursor-pointer"
           :class="{ 'is-open': shown == null }"
           :style="{ '--fill': `${fill}%` }"
           :aria-valuetext="shown != null ? format(shown) : t('model.story.slider_open')"
           :data-testid="`slider-${testKey}`"
           @input="onInput(Number(($event.target as HTMLInputElement).value))"
           @change="commit(Number(($event.target as HTMLInputElement).value))"
           @pointerup="settle($event, true)"
           @blur="settle($event, false)" />
    <p class="-mt-1 flex justify-between gap-2 text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400" :class="compact ? 'xl:hidden' : ''">
      <span aria-hidden="true">{{ format(min) }}</span>
      <span v-if="detail && shown != null" class="text-gray-700 dark:text-gray-300" :data-testid="`slider-${testKey}-detail`">{{ detail(shown) }}</span>
      <span aria-hidden="true">{{ format(max) }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * One slider of the driving profile. `value` null means unanswered: the thumb rests on `start`,
 * greyed, the track stays empty and the value reads "noch offen", so nothing looks chosen that
 * the reader did not choose. The answer is committed on release (change), not on every input.
 */
const props = defineProps<{
  id: string
  testKey: string
  label: string
  min: number
  max: number
  step: number
  start: number
  value: number | null
  format: (value: number) => string
  /** between the scale ends once answered: "4.500 km im Jahr" */
  detail?: (value: number) => string
  highlight?: boolean
  /** From xl the label sits above the value and the min/max line goes, so five sliders fit one row */
  compact?: boolean
}>()
const emit = defineEmits<{ commit: [value: number] }>()
const { t } = useI18n()

/** Value while dragging, before it is committed */
const draft = ref<number | null>(null)
const shown = computed(() => draft.value ?? props.value)
const fill = computed(() => shown.value == null ? 0 : (shown.value - props.min) / (props.max - props.min) * 100)

/** The last value handed out; a release fires both change and pointerup, the answer goes out once */
let committed: number | null = null

function onInput(value: number) {
  draft.value = value
  committed = null
}

function commit(value: number) {
  draft.value = null
  if (value === committed) return
  committed = value
  emit('commit', value)
}

/**
 * Release without a change event: a tap on the thumb of an open slider (5 commute days sit on
 * the edge and cannot be moved there), or a drag back to where it started. Both are answers.
 */
function settle(event: Event, tap: boolean) {
  const value = Number((event.target as HTMLInputElement).value)
  // tabbing past an open slider is no answer, only a tap on it is
  if (draft.value != null || (tap && props.value == null)) commit(value)
}
</script>

<style scoped>
/* Own track and thumb: the native accent colours differ per browser and draw a dark track in Chrome */
.profile-slider {
  -webkit-appearance: none;
  appearance: none;
  background: transparent;
  --track: rgb(229 231 235);
  --filled: rgb(22 163 74);
  --thumb: rgb(22 163 74);
}
.profile-slider.is-open { --thumb: rgb(156 163 175); }
.dark .profile-slider { --track: rgb(55 65 81); --filled: rgb(34 197 94); }
.dark .profile-slider:not(.is-open) { --thumb: rgb(34 197 94); }
.profile-slider::-webkit-slider-runnable-track {
  height: 8px;
  border-radius: 9999px;
  background: linear-gradient(to right, var(--filled) var(--fill), var(--track) var(--fill));
}
.profile-slider::-moz-range-track {
  height: 8px;
  border-radius: 9999px;
  background: linear-gradient(to right, var(--filled) var(--fill), var(--track) var(--fill));
}
.profile-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 28px;
  height: 28px;
  margin-top: -10px;
  border-radius: 9999px;
  background: var(--thumb);
  border: 3px solid white;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
}
.profile-slider::-moz-range-thumb {
  width: 22px;
  height: 22px;
  border-radius: 9999px;
  background: var(--thumb);
  border: 3px solid white;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
}
.profile-slider:focus-visible { outline: none; }
.profile-slider:focus-visible::-webkit-slider-thumb { box-shadow: 0 0 0 4px rgb(22 163 74 / 0.4); }
.profile-slider:focus-visible::-moz-range-thumb { box-shadow: 0 0 0 4px rgb(22 163 74 / 0.4); }
</style>

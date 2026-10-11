<template>
  <section class="grid gap-3.5 pt-9" :aria-labelledby="headingId">
    <p class="flex items-center gap-2 text-[11.5px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">
      <span>{{ title }}</span>
      <span class="inline-flex items-center gap-1 normal-case tracking-normal" :class="levelClass">
        <i class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></i>{{ t(`model.story.level_${level}`) }}
      </span>
    </p>
    <h2 :id="headingId" class="max-w-[24ch] text-[clamp(24px,6vw,36px)] font-bold leading-[1.08] tracking-tight text-gray-900 dark:text-gray-100">
      <span v-for="(line, i) in sentences(headline)" :key="i" class="block">{{ line }}{{ ' ' }}</span>
    </h2>
    <p class="max-w-[58ch] text-gray-600 dark:text-gray-400">{{ say }}</p>

    <dl v-if="tiles.length" class="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-gray-200 bg-gray-200 dark:border-gray-800 dark:bg-gray-800" :class="COLS[tiles.length] ?? 'md:grid-cols-4'">
      <!-- Top-aligned, same rhythm in every cell: number, unit, label -->
      <!-- An odd last tile spans the row on phones, so no empty grey cell shows -->
      <div v-for="tile in tiles" :key="tile.label" class="flex flex-col-reverse justify-end gap-1 bg-white px-4 py-4 last:odd:col-span-2 md:last:odd:col-span-1 dark:bg-gray-900">
        <dt class="text-[13px] leading-snug text-gray-500 dark:text-gray-400">{{ keepUnits(tile.label) }}</dt>
        <dd class="grid gap-2">
          <span class="grid">
            <span class="whitespace-nowrap text-[30px] font-semibold leading-none tracking-tight tabular-nums text-gray-900 md:text-[32px] dark:text-gray-100">{{ splitUnit(tile).number }}</span>
            <span v-if="splitUnit(tile).unit" class="mt-1 whitespace-nowrap text-[13px] font-medium text-gray-500 dark:text-gray-400">{{ splitUnit(tile).unit }}</span>
          </span>
          <span v-if="tile.bar != null" class="h-[6px] overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true">
            <span class="block h-full rounded-full motion-safe:transition-[width] motion-safe:duration-700"
                  :class="tile.tone === 'winter' ? 'bg-sky-500' : 'bg-amber-400'"
                  :style="{ width: `${tile.bar}%` }"></span>
          </span>
        </dd>
      </div>
    </dl>

    <!-- A question the reader was sent back to ("Meine Zahlen" with open questions) is marked until answered -->
    <div v-for="q in questions" :key="q.key" class="grid gap-2.5 rounded-xl pt-2 motion-safe:transition-shadow" :class="q.highlight ? 'ring-2 ring-green-600 ring-offset-8 ring-offset-gray-50 dark:ring-offset-gray-950' : ''" :id="`story-${q.key}`">
      <!-- Slider answers sit on the question line, so an open question takes no extra height -->
      <div class="flex items-baseline justify-between gap-3">
        <p :id="`story-${q.key}-q`" class="min-w-0 text-[17px] font-semibold text-gray-900 md:text-[18px] dark:text-gray-100">{{ q.question }}</p>
        <span v-if="q.slider && shown(q) != null" class="flex-none whitespace-nowrap text-[22px] font-semibold leading-none tracking-tight tabular-nums text-gray-900 md:text-[26px] dark:text-gray-100" :data-testid="`slider-${q.key}-value`">{{ (q.slider.short ?? q.slider.format)(shown(q)!) }}</span>
      </div>
      <!-- Slider: shows the value live while dragging, answers on release, so the band reacts once -->
      <div v-if="q.slider" class="-mt-1 grid">
        <input type="range" :min="q.slider.min" :max="q.slider.max" :step="q.slider.step"
               :value="shown(q) ?? q.slider.start"
               class="story-slider h-8 w-full cursor-pointer"
               :class="{ 'is-open': shown(q) == null }"
               :style="{ '--fill': `${fillPercent(q)}%` }"
               :aria-labelledby="`story-${q.key}-q`"
               :aria-valuetext="shown(q) != null ? q.slider.format(shown(q)!) : t('model.story.slider_open')"
               :data-testid="`slider-${q.key}`"
               @input="drafts[q.key] = Number(($event.target as HTMLInputElement).value)"
               @change="commit(q.key, Number(($event.target as HTMLInputElement).value))" />
        <p class="flex justify-between gap-2 text-[15px] font-medium tabular-nums text-gray-600 dark:text-gray-400">
          <span aria-hidden="true">{{ (q.slider.short ?? q.slider.format)(q.slider.min) }}</span>
          <span v-if="q.slider.detail && shown(q) != null" class="text-gray-900 dark:text-gray-100" :data-testid="`slider-${q.key}-detail`">{{ q.slider.detail(shown(q)!) }}</span>
          <span aria-hidden="true">{{ (q.slider.short ?? q.slider.format)(q.slider.max) }}</span>
        </p>
      </div>
      <!-- Wraps instead of scrolling: four answers must all be visible to choose between them -->
      <div v-else class="flex flex-wrap gap-2" role="group" :aria-labelledby="`story-${q.key}-q`">
        <button v-for="chip in q.chips" :key="String(chip.value)" type="button" :disabled="chip.disabled"
                class="inline-flex min-h-10 flex-none items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:cursor-not-allowed disabled:opacity-40"
                :class="chip.value === q.value
                  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                  : 'bg-gray-200/70 text-gray-800 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
                :aria-pressed="chip.value === q.value"
                :data-testid="`chip-${q.key}-${chip.value}`"
                @click="emit('answer', q.key, chip.value)">
          {{ chip.label }}
        </button>
      </div>
    </div>
    <p v-if="effect" class="text-[13px] leading-snug text-gray-500 dark:text-gray-400">{{ effect }}</p>
    <slot name="extra" />
  </section>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { keepUnits, type ChapterKey, type StoryLevel, type StoryTile } from '../../utils/modelStory'

export interface StoryQuestion {
  key: string
  question: string
  /** disabled: an answer that contradicts another one, e.g. more motorway km than km a year */
  chips: { label: string; value: number | boolean; disabled?: boolean }[]
  /** null while unanswered: no chip shows as pressed */
  value: number | boolean | null
  /** the reader was sent here to answer it before the result */
  highlight?: boolean
  /** set instead of chips for an open range; start is where the thumb rests while unanswered */
  slider?: {
    min: number; max: number; step: number; start: number
    /** full answer, for the profile card and screen readers: "30 % · 4.500 km" */
    format: (value: number) => string
    /** on the question line and the scale ends when shorter than format: "30 %" */
    short?: (value: number) => string
    /** under the slider between the scale ends: "4.500 km a year" */
    detail?: (value: number) => string
  }
}

/**
 * One chapter of the model story: claim, sentence, figures, and the questions whose answers
 * feed the visitor's range band. Presentational; the text comes from buildModelStory.
 */
const props = defineProps<{
  chapterKey: ChapterKey
  level: StoryLevel
  title: string
  headline: string
  say: string
  tiles: StoryTile[]
  questions: StoryQuestion[]
  /** what the answers change, e.g. the motorway consumption the band rests on */
  effect?: string
}>()
const emit = defineEmits<{ answer: [key: string, value: number | boolean] }>()

/** First sentence on its own line: "19,2 kWh/100km." then "25 % mehr als im Prospekt." */
function sentences(text: string): string[] {
  const kept = keepUnits(text)
  const i = kept.indexOf('. ')
  return i < 0 ? [kept] : [kept.slice(0, i + 1), kept.slice(i + 2)]
}

const { t } = useI18n()

/** Slider value while dragging, before it is committed as an answer */
const drafts = reactive<Record<string, number>>({})
function shown(q: StoryQuestion): number | null {
  return drafts[q.key] ?? (typeof q.value === 'number' ? q.value : null)
}
/** Track filled up to the thumb once answered; empty while open, so nothing looks chosen */
function fillPercent(q: StoryQuestion): number {
  const v = shown(q)
  if (v == null || !q.slider) return 0
  return (v - q.slider.min) / (q.slider.max - q.slider.min) * 100
}
function commit(key: string, value: number) {
  delete drafts[key]
  emit('answer', key, value)
}
/** Columns from md up follow the number of tiles, so no empty cell shows */
const COLS: Record<number, string> = { 1: 'md:grid-cols-1', 2: 'md:grid-cols-2', 3: 'md:grid-cols-3', 4: 'md:grid-cols-4' }

/** "19,2 kWh/100km" → number and unit on separate lines; "112 kW" and a value without a space stay whole */
function splitUnit(tile: StoryTile): { number: string; unit: string } {
  if (tile.unit) return { number: keepUnits(tile.value), unit: tile.unit }
  const value = tile.value
  const i = value.lastIndexOf(' ')
  if (i < 0 || !/\d/.test(value.slice(0, i))) return { number: value, unit: '' }
  // Short units ride along at full size ("112 kW"); a rate like kWh/100km is too wide for a phone tile
  if (!value.slice(i + 1).includes('/')) return { number: keepUnits(value), unit: '' }
  return { number: value.slice(0, i), unit: value.slice(i + 1) }
}
const headingId = computed(() => `story-${props.chapterKey}-h`)
const levelClass = computed(() => props.level === 'FULL'
  ? 'text-green-700 dark:text-green-400'
  : props.level === 'PARTIAL' ? 'text-amber-700 dark:text-amber-400' : 'text-gray-500 dark:text-gray-400')
</script>

<style scoped>
/* Own track and thumb: the native accent colours differ per browser and draw a dark track in Chrome */
.story-slider {
  -webkit-appearance: none;
  appearance: none;
  background: transparent;
  --track: rgb(229 231 235);
  --filled: rgb(22 163 74);
  --thumb: rgb(22 163 74);
}
.story-slider.is-open { --thumb: rgb(156 163 175); }
:global(.dark) .story-slider { --track: rgb(55 65 81); --filled: rgb(34 197 94); }
:global(.dark) .story-slider:not(.is-open) { --thumb: rgb(34 197 94); }
.story-slider::-webkit-slider-runnable-track {
  height: 8px;
  border-radius: 9999px;
  background: linear-gradient(to right, var(--filled) var(--fill), var(--track) var(--fill));
}
.story-slider::-moz-range-track {
  height: 8px;
  border-radius: 9999px;
  background: linear-gradient(to right, var(--filled) var(--fill), var(--track) var(--fill));
}
.story-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 26px;
  height: 26px;
  margin-top: -9px;
  border-radius: 9999px;
  background: var(--thumb);
  border: 3px solid white;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
}
.story-slider::-moz-range-thumb {
  width: 20px;
  height: 20px;
  border-radius: 9999px;
  background: var(--thumb);
  border: 3px solid white;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
}
.story-slider:focus-visible { outline: none; }
.story-slider:focus-visible::-webkit-slider-thumb { box-shadow: 0 0 0 3px rgb(22 163 74 / 0.4); }
.story-slider:focus-visible::-moz-range-thumb { box-shadow: 0 0 0 3px rgb(22 163 74 / 0.4); }
</style>
